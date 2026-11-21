package org.restreet.android.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounds-checked reader for the standard PE32 import directory.
 * Xbox XEX import-library metadata is a separate format and is not parsed here.
 */
public final class PeImportDirectory {
    public static final class Symbol {
        public final String name;
        public final int ordinal;
        public final boolean byOrdinal;
        Symbol(String name, int ordinal, boolean byOrdinal) {
            this.name = name; this.ordinal = ordinal; this.byOrdinal = byOrdinal;
        }
    }
    public static final class Library {
        public final String name;
        public final List<Symbol> symbols;
        Library(String name, List<Symbol> symbols) {
            this.name = name;
            this.symbols = Collections.unmodifiableList(symbols);
        }
    }
    public final boolean valid;
    public final String error;
    public final List<Library> libraries;

    private PeImportDirectory(boolean valid, String error, List<Library> libraries) {
        this.valid = valid; this.error = error;
        this.libraries = Collections.unmodifiableList(libraries);
    }

    public static PeImportDirectory parse(byte[] bytes, PowerPcPeImage image) {
        if (bytes == null || image == null || !image.valid) return bad("Imagem PE inválida.");
        if (bytes.length < 64 || bytes[0] != 'M' || bytes[1] != 'Z') return bad("Cabeçalho DOS inválido.");
        long pe = u32(bytes, 0x3c);
        if (!range(pe, 24, bytes.length)) return bad("Cabeçalho PE fora do arquivo.");
        long optional = pe + 24;
        int optionalSize = u16(bytes, (int) pe + 20);
        if (!range(optional, optionalSize, bytes.length) || optionalSize < 112) return bad("Cabeçalho opcional PE32 truncado.");
        int o = (int) optional;
        if (u16(bytes, o) != 0x10b) return bad("Somente PE32 é suportado.");
        long directoryCount = u32(bytes, o + 92);
        if (directoryCount < 2) return new PeImportDirectory(true, "", new ArrayList<Library>());
        long importRva = u32(bytes, o + 104), importSize = u32(bytes, o + 108);
        if (importRva == 0 && importSize == 0) return new PeImportDirectory(true, "", new ArrayList<Library>());
        if (importRva == 0 || importSize < 20) return bad("Diretório de importação inconsistente.");
        long importOffset = image.fileOffsetForRva(importRva, bytes.length);
        if (importOffset < 0 || importSize > bytes.length - importOffset)
            return bad("Diretório de importação excede os dados disponíveis.");
        long mappedBytes = mappedRawBytes(image, importRva);
        if (mappedBytes < importSize) return bad("Diretório de importação excede a seção PE mapeada.");
        long descriptorCount = Math.min(importSize / 20, 4096);
        List<Library> libraries = new ArrayList<>();
        boolean terminated = false;
        for (int i = 0; i < descriptorCount; i++) {
            long descriptorRva = importRva + i * 20L;
            long originalThunk = readRva32(bytes, image, descriptorRva);
            long time = readRva32(bytes, image, descriptorRva + 4);
            long forwarder = readRva32(bytes, image, descriptorRva + 8);
            long nameRva = readRva32(bytes, image, descriptorRva + 12);
            long firstThunk = readRva32(bytes, image, descriptorRva + 16);
            if (originalThunk < 0 || time < 0 || forwarder < 0 || nameRva < 0 || firstThunk < 0)
                return bad("Descritor de importação fora da imagem.");
            if ((originalThunk | time | forwarder | nameRva | firstThunk) == 0) {
                terminated = true; break;
            }
            String libraryName = readCString(bytes, image, nameRva, 512);
            if (libraryName == null || libraryName.isEmpty()) return bad("Nome de biblioteca inválido.");
            long thunkRva = originalThunk != 0 ? originalThunk : firstThunk;
            List<Symbol> symbols = new ArrayList<>();
            boolean thunkTerminated = false;
            for (int n = 0; n < 65536; n++) {
                long thunk = readRva32(bytes, image, thunkRva + n * 4L);
                if (thunk < 0) return bad("Tabela thunk fora da imagem.");
                if (thunk == 0) { thunkTerminated = true; break; }
                if ((thunk & 0x80000000L) != 0) {
                    symbols.add(new Symbol("", (int)(thunk & 0xffff), true));
                } else {
                    String symbolName = readCString(bytes, image, thunk + 2, 1024);
                    if (symbolName == null || symbolName.isEmpty()) return bad("Nome de função importada inválido.");
                    symbols.add(new Symbol(symbolName, 0, false));
                }
            }
            if (!thunkTerminated) return bad("Tabela thunk sem terminador.");
            libraries.add(new Library(libraryName, symbols));
        }
        if (!terminated) return bad("Diretório de importação sem descritor terminador.");
        return new PeImportDirectory(true, "", libraries);
    }

    private static long mappedRawBytes(PowerPcPeImage image, long rva) {
        if (rva < image.headersSize) return image.headersSize - rva;
        for (PowerPcPeImage.Section section : image.sections) {
            long delta = rva - section.virtualAddress;
            if (delta >= 0 && delta < section.rawSize) return section.rawSize - delta;
        }
        return 0;
    }
    private static long readRva32(byte[] b, PowerPcPeImage image, long rva) {
        long off = image.fileOffsetForRva(rva, b.length);
        return off >= 0 && range(off, 4, b.length) ? u32(b, (int)off) : -1;
    }
    private static String readCString(byte[] b, PowerPcPeImage image, long rva, int limit) {
        long off = image.fileOffsetForRva(rva, b.length);
        if (off < 0) return null;
        int start = (int)off, end = start;
        int max = (int)Math.min((long)b.length, off + limit);
        while (end < max && b[end] != 0) end++;
        if (end == max) return null;
        return new String(b, start, end - start, java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static PeImportDirectory bad(String error) {
        return new PeImportDirectory(false, error, new ArrayList<Library>());
    }
    private static boolean range(long off, long len, long total) {
        return off >= 0 && len >= 0 && off <= total && len <= total - off;
    }
    private static int u16(byte[] b, int p) { return (b[p]&255) | ((b[p+1]&255)<<8); }
    private static long u32(byte[] b, int p) {
        return (b[p]&255L) | ((b[p+1]&255L)<<8) | ((b[p+2]&255L)<<16) | ((b[p+3]&255L)<<24);
    }
}
