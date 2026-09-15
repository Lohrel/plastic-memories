package dev.lohrel.plasticmemories.network;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SkillClientPayloadHandler {
    private SkillClientPayloadHandler() {
    }

    public static void handle(SkillResultPayload payload, IPayloadContext context) {
        context.player().displayClientMessage(Component.literal(SkillResultMessages.message(payload.result())), false);
    }
}
