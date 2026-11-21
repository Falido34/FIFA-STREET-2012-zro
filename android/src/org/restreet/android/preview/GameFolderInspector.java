package org.restreet.android.preview;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Read-only inventory of a user-selected Xbox 360 game folder through SAF. */
public final class GameFolderInspector {
    private static final int MAX_ENTRIES = 50000;
    private static final int MAX_DEPTH = 64;

    private GameFolderInspector() {}

    public static String inspect(Context context, Uri tree) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException e) {
            throw new IOException("Não foi possível abrir a pasta selecionada.", e);
        }
        State state = new State(resolver, tree);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        walk(state, root, "", 0);
        Collections.sort(state.modules, (left, right) -> left.path.compareToIgnoreCase(right.path));
        StringBuilder report = new StringBuilder();
        report.append("Análise da pasta do FIFA Street 3\n");
        report.append("Arquivos examinados: ").append(state.entries).append("\n");
        report.append("Executáveis XEX/DLL: ").append(state.modules.size()).append("\n");
        report.append("default.xex na raiz: ").append(state.rootDefault ? "encontrado" : "NÃO encontrado").append("\n");
        report.append("Cabeçalhos XEX2 válidos: ").append(state.validXex).append("\n");
        if (state.defaultMetadata != null) {
            XexMetadata m = state.defaultMetadata;
            report.append("Identificação pelo default.xex: ");
            report.append(m.gameName.isEmpty() ? "não confirmada" : m.gameName).append("\n");
            report.append("Title ID: ").append(m.titleId.isEmpty() ? "não encontrado" : m.titleId).append("\n");
            if (!m.mediaId.isEmpty()) report.append("Media ID: ").append(m.mediaId).append("\n");
            if (!m.version.isEmpty()) report.append("Versão XEX: ").append(m.version).append(" / base ").append(m.baseVersion).append("\n");
            if (m.discCount > 0) report.append("Disco: ").append(m.discNumber).append(" de ").append(m.discCount).append("\n");
            report.append("Cabeçalho XEX: 0x").append(Long.toHexString(m.headerSize).toUpperCase(Locale.ROOT)).append("\n");
            report.append("Security info: 0x").append(Long.toHexString(m.securityInfoOffset).toUpperCase(Locale.ROOT)).append("\n");
            if (m.entryPoint >= 0) report.append("Entry point: 0x").append(Long.toHexString(m.entryPoint).toUpperCase(Locale.ROOT)).append("\n");
            if (m.imageBase >= 0) report.append("Image base: 0x").append(Long.toHexString(m.imageBase).toUpperCase(Locale.ROOT)).append("\n");
            report.append("Formato da imagem PE: ").append(m.encryptionName()).append(" / ").append(m.compressionName()).append("\n");
            if (m.compressionType == 2 && m.lzxWindowSize > 0)
                report.append("Janela LZX: ").append(m.lzxWindowSize).append(" bytes\n");
            report.append("Bibliotecas importadas: ").append(m.importLibraries.size()).append("\n");
            for (XexMetadata.ImportLibrary library : m.importLibraries) {
                report.append("  - ").append(library.name)
                      .append(" | imports: ").append(library.importCount)
                      .append(" | versão: ").append(library.version)
                      .append(" | mínima: ").append(library.minVersion).append("\n");
            }
            report.append("Ordinais individuais: disponíveis após extração/análise da imagem PE\n");
            if (!state.defaultSha256.isEmpty()) report.append("SHA-256 default.xex: ").append(state.defaultSha256).append("\n");
        }
        report.append("\nMódulos encontrados:\n");
        if (state.modules.isEmpty()) report.append("Nenhum arquivo .xex ou .dll encontrado.\n");
        int shown = Math.min(40, state.modules.size());
        for (int i = 0; i < shown; i++) {
            Module module = state.modules.get(i);
            report.append(module.valid ? "[XEX2] " : "[sem XEX2] ")
                  .append(module.path).append(" (").append(module.size).append(" bytes)\n");
        }
        if (state.modules.size() > shown) report.append("... e mais ").append(state.modules.size() - shown).append(" módulo(s).\n");
        report.append("\nA análise não copia nem modifica os arquivos do jogo.");
        return report.toString();
    }

    private static void walk(State state, Uri directory, String parent, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("A estrutura de pastas é profunda demais.");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                state.tree, DocumentsContract.getDocumentId(directory));
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = state.resolver.query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("O armazenamento não permitiu listar a pasta.");
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Análise cancelada.");
                if (++state.entries > MAX_ENTRIES) throw new IOException("A pasta contém itens demais para analisar.");
                String name = cursor.getString(nameCol);
                validateName(name);
                String path = parent.isEmpty() ? name : parent + "/" + name;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(
                        state.tree, cursor.getString(idCol));
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeCol))) {
                    walk(state, child, path, depth + 1);
                } else {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (path.equalsIgnoreCase("default.xex")) state.rootDefault = true;
                    if (lower.endsWith(".xex") || lower.endsWith(".dll") && lower.contains(".xex")) {
                        long size = cursor.isNull(sizeCol) ? -1 : cursor.getLong(sizeCol);
                        boolean valid = hasXex2(state.resolver, child);
                        if (valid) state.validXex++;
                        if (path.equalsIgnoreCase("default.xex")) readDefaultMetadata(state, child);
                        state.modules.add(new Module(path, size, valid));
                    }
                }
            }
        } catch (SecurityException e) {
            throw new IOException("Sem permissão para ler a pasta selecionada.", e);
        }
    }

    private static void readDefaultMetadata(State state, Uri file) throws IOException {
        try (InputStream in = state.resolver.openInputStream(file)) {
            if (in == null) throw new IOException("Não foi possível ler default.xex.");
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 indisponível.", e); }
            ByteArrayOutputStream header = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                if (header.size() < 1024 * 1024) {
                    int keep = Math.min(count, 1024 * 1024 - header.size());
                    header.write(buffer, 0, keep);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
            state.defaultSha256 = hex.toString();
            state.defaultMetadata = XexMetadata.parse(header.toByteArray());
        }
    }

    private static boolean hasXex2(ContentResolver resolver, Uri file) throws IOException {
        try (InputStream in = resolver.openInputStream(file)) {
            if (in == null) return false;
            byte[] magic = new byte[4];
            int offset = 0;
            while (offset < magic.length) {
                int count = in.read(magic, offset, magic.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            return magic[0] == 'X' && magic[1] == 'E' && magic[2] == 'X' && magic[3] == '2';
        }
    }

    private static void validateName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
            throw new IOException("A pasta contém um nome inválido.");
    }

    private static final class State {
        final ContentResolver resolver;
        final Uri tree;
        final List<Module> modules = new ArrayList<>();
        int entries;
        int validXex;
        boolean rootDefault;
        XexMetadata defaultMetadata;
        String defaultSha256 = "";
        State(ContentResolver resolver, Uri tree) { this.resolver = resolver; this.tree = tree; }
    }

    private static final class Module {
        final String path;
        final long size;
        final boolean valid;
        Module(String path, long size, boolean valid) { this.path = path; this.size = size; this.valid = valid; }
    }
}
