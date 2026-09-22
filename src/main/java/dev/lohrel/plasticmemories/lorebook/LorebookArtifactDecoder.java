package dev.lohrel.plasticmemories.lorebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bounded container decoder. It only returns JSON text; semantic format
 * detection remains in LorebookImporter.
 */
public final class LorebookArtifactDecoder {
    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private LorebookArtifactDecoder() {
    }

    public static DecodedArtifact decode(byte[] input) throws ArtifactDecodingException {
        if (input == null || input.length > LorebookLimits.MAX_RAW_INPUT_BYTES) {
            throw new ArtifactDecodingException();
        }
        if (isPng(input)) {
            return new DecodedArtifact(decodePng(input), Optional.of(LorebookSourceFormat.PNG_CHARACTER_CARD));
        }
        if (isZip(input)) {
            return new DecodedArtifact(decodeCharx(input), Optional.of(LorebookSourceFormat.CHARX_CHARACTER_CARD));
        }
        return new DecodedArtifact(decodeUtf8(input), Optional.empty());
    }

    private static String decodePng(byte[] input) throws ArtifactDecodingException {
        int offset = PNG_SIGNATURE.length;
        String chara = null;
        String ccv3 = null;
        boolean sawIend = false;
        while (offset < input.length) {
            if (input.length - offset < 12) {
                throw new ArtifactDecodingException();
            }
            int length = ByteBuffer.wrap(input, offset, Integer.BYTES).getInt();
            offset += Integer.BYTES;
            if (length < 0 || length > input.length - offset - 8) {
                throw new ArtifactDecodingException();
            }
            byte[] type = Arrays.copyOfRange(input, offset, offset + 4);
            offset += 4;
            byte[] data = Arrays.copyOfRange(input, offset, offset + length);
            offset += length;
            long suppliedCrc = Integer.toUnsignedLong(ByteBuffer.wrap(input, offset, Integer.BYTES).getInt());
            offset += Integer.BYTES;
            CRC32 crc = new CRC32();
            crc.update(type);
            crc.update(data);
            if (crc.getValue() != suppliedCrc) {
                throw new ArtifactDecodingException();
            }
            String typeName = new String(type, StandardCharsets.ISO_8859_1);
            if ("tEXt".equals(typeName)) {
                String[] text = textChunk(data);
                if ("chara".equals(text[0])) {
                    chara = text[1];
                } else if ("ccv3".equals(text[0])) {
                    ccv3 = text[1];
                }
            } else if ("zTXt".equals(typeName)) {
                String[] text = compressedTextChunk(data, StandardCharsets.ISO_8859_1);
                if ("chara".equals(text[0])) {
                    chara = text[1];
                } else if ("ccv3".equals(text[0])) {
                    ccv3 = text[1];
                }
            } else if ("iTXt".equals(typeName)) {
                String[] text = internationalTextChunk(data);
                if ("chara".equals(text[0])) {
                    chara = text[1];
                } else if ("ccv3".equals(text[0])) {
                    ccv3 = text[1];
                }
            } else if ("IEND".equals(typeName)) {
                if (length != 0 || offset != input.length) {
                    throw new ArtifactDecodingException();
                }
                sawIend = true;
                break;
            }
        }
        if (!sawIend || (ccv3 == null && chara == null)) {
            throw new ArtifactDecodingException();
        }
        return decodeMetadataPayload(ccv3 != null ? ccv3 : chara);
    }

    private static String decodeCharx(byte[] input) throws ArtifactDecodingException {
        String card = null;
        int entries = 0;
        long decompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > LorebookLimits.MAX_ARCHIVE_ENTRIES || unsafeArchivePath(entry.getName())) {
                    throw new ArtifactDecodingException();
                }
                byte[] data = readBoundedEntry(zip, LorebookLimits.MAX_DECOMPRESSED_INPUT_BYTES - decompressed);
                decompressed += data.length;
                if (decompressed > LorebookLimits.MAX_DECOMPRESSED_INPUT_BYTES) {
                    throw new ArtifactDecodingException();
                }
                if (!entry.isDirectory() && "card.json".equals(entry.getName())) {
                    if (card != null) {
                        throw new ArtifactDecodingException();
                    }
                    card = decodeUtf8(data);
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new ArtifactDecodingException();
        }
        if (card == null) {
            throw new ArtifactDecodingException();
        }
        return card;
    }

    private static boolean unsafeArchivePath(String name) {
        return name == null
                || name.isBlank()
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("\\")
                || name.contains("../")
                || name.equals("..");
    }

    private static byte[] readBoundedEntry(ZipInputStream input, long remaining) throws IOException, ArtifactDecodingException {
        if (remaining < 0) {
            throw new ArtifactDecodingException();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > remaining - output.size()) {
                throw new ArtifactDecodingException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String[] textChunk(byte[] data) throws ArtifactDecodingException {
        int separator = indexOfNull(data, 0);
        if (separator <= 0) {
            throw new ArtifactDecodingException();
        }
        return new String[] {
            new String(data, 0, separator, StandardCharsets.ISO_8859_1),
            new String(data, separator + 1, data.length - separator - 1, StandardCharsets.ISO_8859_1)
        };
    }

    private static String[] compressedTextChunk(byte[] data, java.nio.charset.Charset charset)
            throws ArtifactDecodingException {
        int separator = indexOfNull(data, 0);
        if (separator <= 0 || separator + 2 > data.length || data[separator + 1] != 0) {
            throw new ArtifactDecodingException();
        }
        return new String[] {
            new String(data, 0, separator, StandardCharsets.ISO_8859_1),
            new String(decompress(data, separator + 2), charset)
        };
    }

    private static String[] internationalTextChunk(byte[] data) throws ArtifactDecodingException {
        int keywordEnd = indexOfNull(data, 0);
        if (keywordEnd <= 0 || keywordEnd + 3 > data.length) {
            throw new ArtifactDecodingException();
        }
        int compressionFlag = Byte.toUnsignedInt(data[keywordEnd + 1]);
        int compressionMethod = Byte.toUnsignedInt(data[keywordEnd + 2]);
        if ((compressionFlag != 0 && compressionFlag != 1) || (compressionFlag == 1 && compressionMethod != 0)) {
            throw new ArtifactDecodingException();
        }
        int languageEnd = indexOfNull(data, keywordEnd + 3);
        if (languageEnd < 0) {
            throw new ArtifactDecodingException();
        }
        int translatedEnd = indexOfNull(data, languageEnd + 1);
        if (translatedEnd < 0) {
            throw new ArtifactDecodingException();
        }
        byte[] text = Arrays.copyOfRange(data, translatedEnd + 1, data.length);
        byte[] decoded = compressionFlag == 1 ? decompress(text, 0) : text;
        return new String[] {
            new String(data, 0, keywordEnd, StandardCharsets.ISO_8859_1),
            decodeUtf8(decoded)
        };
    }

    private static byte[] decompress(byte[] data, int offset) throws ArtifactDecodingException {
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(data, offset, data.length - offset))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inflater.read(buffer)) >= 0) {
                if (read > LorebookLimits.MAX_DECOMPRESSED_INPUT_BYTES - output.size()) {
                    throw new ArtifactDecodingException();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ArtifactDecodingException();
        }
    }

    private static String decodeMetadataPayload(String metadata) throws ArtifactDecodingException {
        String trimmed = metadata.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            return decodeUtf8(Base64.getDecoder().decode(trimmed));
        } catch (IllegalArgumentException exception) {
            throw new ArtifactDecodingException();
        }
    }

    private static String decodeUtf8(byte[] bytes) throws ArtifactDecodingException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ArtifactDecodingException();
        }
    }

    private static int indexOfNull(byte[] data, int start) {
        for (int index = start; index < data.length; index++) {
            if (data[index] == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isPng(byte[] input) {
        return input.length >= PNG_SIGNATURE.length && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(input, PNG_SIGNATURE.length));
    }

    private static boolean isZip(byte[] input) {
        return input.length >= 2 && input[0] == 'P' && input[1] == 'K';
    }

    public record DecodedArtifact(String json, Optional<LorebookSourceFormat> containerFormat) {
        public DecodedArtifact {
            if (json == null || containerFormat == null) {
                throw new IllegalArgumentException("Invalid decoded artifact.");
            }
        }
    }

    public static final class ArtifactDecodingException extends Exception {
        private ArtifactDecodingException() {
        }
    }
}
