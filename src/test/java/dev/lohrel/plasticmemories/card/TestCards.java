package dev.lohrel.plasticmemories.card;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds real card files for tests: a valid 1x1 PNG with a "chara" chunk, JSON cards and .charx zips. */
final class TestCards {
    private TestCards() {
    }

    static String cardJson(String name) {
        return "{\"spec\":\"chara_card_v2\",\"spec_version\":\"2.0\",\"data\":{\"name\":\"" + name
                + "\",\"description\":\"About " + name + ".\","
                + "\"character_book\":{\"entries\":[{\"id\":1,\"keys\":[\"gate\"],\"content\":\"" + name
                + " guards the gate.\",\"enabled\":true,\"insertion_order\":10}]}}}";
    }

    static Path writePng(Path file, String name) throws Exception {
        Files.write(file, png(cardJson(name)));
        return file;
    }

    static Path writeJson(Path file, String name) throws Exception {
        Files.writeString(file, cardJson(name));
        return file;
    }

    static Path writeCharx(Path file, String name, byte[] icon) throws Exception {
        String card = "{\"spec\":\"chara_card_v3\",\"spec_version\":\"3.0\",\"data\":{\"name\":\"" + name
                + "\",\"assets\":[{\"type\":\"icon\",\"uri\":\"embeded://assets/icon/images/main.png\",\"name\":\"main\",\"ext\":\"png\"}]}}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("card.json"));
            zip.write(card.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/icon/images/main.png"));
            zip.write(icon);
            zip.closeEntry();
        }
        return file;
    }

    static byte[] png(String cardJson) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(1);
        headerData.writeInt(1);
        headerData.write(new byte[] {8, 6, 0, 0, 0});
        writeChunk(output, "IHDR", header.toByteArray());
        if (cardJson != null) {
            String encoded = Base64.getEncoder().encodeToString(cardJson.getBytes(StandardCharsets.UTF_8));
            writeChunk(output, "tEXt", ("chara\0" + encoded).getBytes(StandardCharsets.ISO_8859_1));
        }
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, (byte) 200, 100, 50, (byte) 255});
        deflater.finish();
        byte[] compressed = new byte[64];
        int length = deflater.deflate(compressed);
        writeChunk(output, "IDAT", java.util.Arrays.copyOf(compressed, length));
        writeChunk(output, "IEND", new byte[0]);
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
