package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PngCharacterCardImportTest {
    @TempDir
    Path tempDir;

    @Test
    void prefersCcv3MetadataOverCharaMetadataWithoutTrustingTheFilename() throws Exception {
        String chara = Base64.getEncoder().encodeToString("""
                {"spec":"chara_card_v2","data":{"name":"Legacy"}}
                """.getBytes(StandardCharsets.UTF_8));
        String ccv3 = Base64.getEncoder().encodeToString("""
                {"spec":"chara_card_v3","data":{"name":"Preferred"}}
                """.getBytes(StandardCharsets.UTF_8));
        Path source = tempDir.resolve("not-a-card-name.bin");
        Files.write(source, pngWithText("chara", chara, "ccv3", ccv3));

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.PNG_CHARACTER_CARD, result.format());
        assertEquals("Preferred", result.characterCard().orElseThrow().name());
        assertEquals(CompatibilityProfile.SILLY_TAVERN, result.profile());
        assertTrue(result.activationPossible());
    }

    private static byte[] pngWithText(String firstKey, String firstValue, String secondKey, String secondValue) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        writeChunk(output, "IHDR", new byte[] {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0});
        writeChunk(output, "tEXt", (firstKey + "\u0000" + firstValue).getBytes(StandardCharsets.ISO_8859_1));
        writeChunk(output, "tEXt", (secondKey + "\u0000" + secondValue).getBytes(StandardCharsets.ISO_8859_1));
        writeChunk(output, "IEND", new byte[0]);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeChunk(DataOutputStream output, String type, byte[] data) throws Exception {
        output.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        output.write(typeBytes);
        output.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        output.writeInt((int) crc.getValue());
    }
}
