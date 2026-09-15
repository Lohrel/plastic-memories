package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.network.SkillResultCode;
import dev.lohrel.plasticmemories.network.SkillResultPayload;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

final class CookTask implements NpcTask {
    private static final int MAX_DURATION_TICKS = 20 * 120;
    private static final int MAX_CONSECUTIVE_PATH_FAILURES = 5;
    private static final int TARGET_INTERACTION_TICKS = 60;
    private static final int MAX_DELIVERY_QUANTITY = 4;
    private static final double DELIVERY_DISTANCE_SQUARED = 2.5 * 2.5;
    private static final double MOVEMENT_SPEED = 0.65;

    private final ServerPlayer player;
    private final NpcHandle npc;
    private final ServerLevel originLevel;
    private final long requestId;
    private final long deadline;
    private final List<BlockPos> containerTargets;
    private final CookContainerSearchProgress containerProgress;
    private final PathFailureTracker pathFailures = new PathFailureTracker(MAX_CONSECUTIVE_PATH_FAILURES);
    private BlockPos openedContainerPosition;
    private boolean reachedContainer;
    private boolean failedContainerPath;
    private boolean finished;

    private CookTask(
            ServerPlayer player,
            NpcHandle npc,
            ServerLevel originLevel,
            long requestId,
            long deadline,
            List<BlockPos> containerTargets,
            CookContainerSearchProgress containerProgress) {
        this.player = player;
        this.npc = npc;
        this.originLevel = originLevel;
        this.requestId = requestId;
        this.deadline = deadline;
        this.containerTargets = containerTargets;
        this.containerProgress = containerProgress;
    }

    static StartResult start(ServerPlayer player, NpcHandle npc, long requestId) {
        ServerLevel originLevel = player.serverLevel();
        boolean npcHasFood = CookInventoryTransfer.hasFood(npc.inventory());
        List<BlockPos> targets = npcHasFood
                ? List.of()
                : NearbyContainerLocator.find(originLevel, npc.entity());
        CookStartPlanner.Result start = CookStartPlanner.resolve(npcHasFood, targets.size());
        if (start == CookStartPlanner.Result.NO_CONTAINER) {
            return StartResult.failed(SkillResultCode.NO_CONTAINER);
        }
        CookContainerSearchProgress progress = start == CookStartPlanner.Result.SEARCH_CONTAINERS
                ? new CookContainerSearchProgress(targets.size(), TARGET_INTERACTION_TICKS)
                : null;
        return StartResult.started(new CookTask(
                player,
                npc,
                originLevel,
                requestId,
                originLevel.getGameTime() + MAX_DURATION_TICKS,
                targets,
                progress));
    }

    @Override
    public boolean tick() {
        if (finished) {
            return true;
        }
        if (!isStillValid()) {
            finish(SkillResultCode.INTERRUPTED);
            return true;
        }
        if (player.isSpectator() || !npc.canAssignSkill(player)) {
            finish(SkillResultCode.PERMISSION_DENIED);
            return true;
        }
        if (!npc.availableForSkill()) {
            finish(SkillResultCode.INTERRUPTED);
            return true;
        }
        long gameTime = originLevel.getGameTime();
        if (gameTime > deadline) {
            finish(SkillResultCode.NO_PATH);
            return true;
        }
        if (containerProgress == null
                || containerProgress.phase() == CookContainerSearchProgress.Phase.CARRYING) {
            tickDelivery(gameTime);
        } else if (containerProgress.phase() == CookContainerSearchProgress.Phase.MOVING) {
            tickMovingToContainer(gameTime);
        } else if (containerProgress.phase() == CookContainerSearchProgress.Phase.EXAMINING) {
            tickExaminingContainer(gameTime);
        } else {
            finish(containerExhaustionResult());
        }
        return finished;
    }

    @Override
    public boolean belongsTo(UUID playerId) {
        return player.getUUID().equals(playerId);
    }

    @Override
    public void abortForShutdown() {
        if (!finished) {
            closeContainer();
            npc.entity().getNavigation().stop();
            finished = true;
        }
    }

    private boolean isStillValid() {
        return player.isAlive()
                && !player.hasDisconnected()
                && CookTaskLevelPolicy.remainsInOrigin(
                        originLevel, player.serverLevel(), npc.entity().level())
                && npc.entity().isAlive()
                && !npc.entity().isRemoved()
                && CookDistancePolicy.withinTaskRange(npc.entity().distanceToSqr(player));
    }

    private void tickMovingToContainer(long gameTime) {
        BlockPos target = currentTarget();
        ServerLevel level = originLevel;
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null
                || !NearbyContainerLocator.supports(level.getBlockState(target).getBlock())) {
            skipCurrentContainer(false);
            return;
        }
        npc.entity().getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (ContainerInteractionRange.isWithinArrivalDistance(
                npc.entity().distanceToSqr(target.getCenter()))) {
            if (ContainerInteractionEffects.isOccupied(level, target)) {
                npc.entity().getNavigation().stop();
                return;
            }
            var resolved = VanillaContainerAccess.resolveAccessible(level, target, player);
            if (resolved.isEmpty()) {
                skipCurrentContainer(false);
                return;
            }
            npc.entity().getNavigation().stop();
            openedContainerPosition = target;
            ContainerInteractionEffects.begin(level, target);
            npc.entity().swing(InteractionHand.MAIN_HAND);
            reachedContainer = true;
            pathFailures.recordSuccess();
            containerProgress.arrived(gameTime);
            return;
        }
        if (gameTime % 10 == 0) {
            boolean moving = npc.entity().getNavigation().moveTo(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, MOVEMENT_SPEED);
            if (moving) {
                pathFailures.recordSuccess();
            } else if (pathFailures.recordFailure()) {
                skipCurrentContainer(true);
            }
        }
    }

    private void tickExaminingContainer(long gameTime) {
        BlockPos target = currentTarget();
        npc.entity().getNavigation().stop();
        npc.entity().getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (!ContainerInteractionRange.isWithinRetentionDistance(
                npc.entity().distanceToSqr(target.getCenter()))) {
            closeContainer();
            containerProgress.interactionInterrupted();
            return;
        }
        ContainerInteractionEffects.maintain(originLevel, target);
        if (!containerProgress.readyToInspect(gameTime)) {
            return;
        }

        closeContainer();
        var liveContainer = VanillaContainerAccess.resolveAccessible(originLevel, target, player);
        if (liveContainer.isEmpty()) {
            containerProgress.inspected(false);
            pathFailures.recordSuccess();
            finishIfContainersExhausted();
            return;
        }
        CookInventoryTransfer.Result transfer = CookInventoryTransfer.moveFood(
                liveContainer.orElseThrow(),
                npc.inventory(),
                npc.inventory().getContainerSize(),
                MAX_DELIVERY_QUANTITY);
        if (transfer.result() == CookBatchTransferPlan.Result.READY) {
            containerProgress.inspected(true);
            pathFailures.recordSuccess();
            return;
        }
        if (transfer.result() == CookBatchTransferPlan.Result.INVENTORY_FULL) {
            finish(SkillResultCode.NPC_INVENTORY_FULL);
            return;
        }
        containerProgress.inspected(false);
        pathFailures.recordSuccess();
        finishIfContainersExhausted();
    }

    private void tickDelivery(long gameTime) {
        if (npc.entity().distanceToSqr(player) <= DELIVERY_DISTANCE_SQUARED) {
            deliver();
            return;
        }
        if (gameTime % 10 == 0) {
            if (npc.entity().getNavigation().moveTo(player, MOVEMENT_SPEED)) {
                pathFailures.recordSuccess();
            } else if (pathFailures.recordFailure()) {
                finish(SkillResultCode.NO_PATH);
            }
        }
    }

    private void deliver() {
        npc.entity().getNavigation().stop();
        CookInventoryTransfer.Result transfer = CookInventoryTransfer.moveFood(
                npc.inventory(), player.getInventory(), Inventory.INVENTORY_SIZE, MAX_DELIVERY_QUANTITY);
        if (transfer.result() == CookBatchTransferPlan.Result.NO_FOOD) {
            finish(SkillResultCode.NO_FOOD);
        } else if (transfer.result() == CookBatchTransferPlan.Result.INVENTORY_FULL) {
            finish(SkillResultCode.INVENTORY_FULL);
        } else {
            finish(SkillResultCode.SUCCESS);
        }
    }

    private BlockPos currentTarget() {
        return containerTargets.get(containerProgress.currentIndex().orElseThrow());
    }

    private void skipCurrentContainer(boolean pathFailed) {
        closeContainer();
        if (pathFailed) {
            failedContainerPath = true;
        }
        containerProgress.pathFailed();
        pathFailures.recordSuccess();
        finishIfContainersExhausted();
    }

    private void finishIfContainersExhausted() {
        if (containerProgress.phase() == CookContainerSearchProgress.Phase.EXHAUSTED) {
            finish(containerExhaustionResult());
        }
    }

    private SkillResultCode containerExhaustionResult() {
        if (reachedContainer) {
            return SkillResultCode.NO_FOOD;
        }
        return failedContainerPath ? SkillResultCode.NO_PATH : SkillResultCode.NO_CONTAINER;
    }

    private void closeContainer() {
        if (openedContainerPosition != null) {
            ContainerInteractionEffects.end(originLevel, openedContainerPosition);
            openedContainerPosition = null;
        }
    }

    private void finish(SkillResultCode result) {
        closeContainer();
        npc.entity().getNavigation().stop();
        finished = true;
        PacketDistributor.sendToPlayer(player, new SkillResultPayload(npc.id(), requestId, result));
    }

    record StartResult(CookTask task, SkillResultCode failure) {
        static StartResult started(CookTask task) {
            return new StartResult(task, null);
        }

        static StartResult failed(SkillResultCode failure) {
            return new StartResult(null, failure);
        }

        boolean started() {
            return task != null;
        }
    }
}
