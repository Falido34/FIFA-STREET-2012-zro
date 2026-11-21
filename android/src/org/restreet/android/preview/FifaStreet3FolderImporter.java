package org.restreet.android.preview;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.DocumentsContract;
import java.io.*;

/** SAF importer for the independently stored FIFA Street 3 game directory. */
public final class FifaStreet3FolderImporter {
    public interface Progress { void update(String message) throws IOException; }
    private static final int MAX_ENTRIES=100000;
    private final Context context;
    private final ContentResolver resolver;
    private final File destination;
    private final Progress progress;
    private int entries;
    private boolean foundDefault;

    private FifaStreet3FolderImporter(Context c,File d,Progress p) {
        context=c.getApplicationContext();resolver=c.getContentResolver();destination=d;progress=p;
    }
    public static void importFolder(Context c,Uri tree,File destination,Progress progress) throws IOException {
        if(destination.exists())throw new IOException("A pasta temporária já existe.");
        if(!destination.mkdirs())throw new IOException("Não foi possível criar a pasta temporária.");
        FifaStreet3FolderImporter importer=new FifaStreet3FolderImporter(c,destination,progress);
        try {
            String rootId=DocumentsContract.getTreeDocumentId(tree);
            importer.copyDirectory(tree,DocumentsContract.buildDocumentUriUsingTree(tree,rootId),"");
            importer.validate();
            try(FileOutputStream marker=new FileOutputStream(new File(destination,".restreet-fifa3-import-complete"))) {
                marker.write("4541083B".getBytes("US-ASCII"));
            }
        } catch(Exception e) {
            deleteTree(destination);
            if(e instanceof IOException)throw (IOException)e;
            throw new IOException("Não foi possível ler a pasta selecionada.",e);
        }
    }
    private void check() throws IOException {if(Thread.currentThread().isInterrupted())throw new IOException("Importação cancelada.");}
    private void copyDirectory(Uri tree,Uri directory,String parent) throws IOException {
        check();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(directory));
        String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor cursor=resolver.query(children,cols,null,null,null)) {
            if(cursor==null)throw new IOException("O armazenamento não permitiu listar a pasta.");
            int id=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int name=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mime=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while(cursor.moveToNext()) {
                check();if(++entries>MAX_ENTRIES)throw new IOException("A pasta contém itens demais.");
                String n=cursor.getString(name);
                if(n==null||n.isEmpty()||n.equals(".")||n.equals("..")||n.indexOf('/')>=0||n.indexOf('\\')>=0||n.indexOf(':')>=0||n.endsWith(".")||n.endsWith(" "))
                    throw new IOException("A pasta contém um nome de arquivo inválido.");
                String relative=parent.isEmpty()?n:parent+"/"+n;
                progress.update("Importando "+relative);
                Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(id));
                File target=new File(destination,relative);
                String prefix=destination.getCanonicalPath()+File.separator;
                if(!target.getCanonicalPath().startsWith(prefix))throw new IOException("Caminho inseguro.");
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mime))) {
                    if(!target.mkdirs()&&!target.isDirectory())throw new IOException("Não foi possível criar "+relative);
                    copyDirectory(tree,child,relative);
                } else {
                    File p=target.getParentFile();if(!p.isDirectory()&&!p.mkdirs())throw new IOException("Não foi possível criar a pasta de destino.");
                    try(InputStream in=resolver.openInputStream(child);OutputStream out=new BufferedOutputStream(new FileOutputStream(target))) {
                        if(in==null)throw new IOException("Não foi possível abrir "+relative);
                        byte[] buffer=new byte[1024*1024];int read;
                        while((read=in.read(buffer))!=-1) {
                            check();
                            if(new StatFs(context.getFilesDir().getPath()).getAvailableBytes()<read+64L*1024*1024)throw new IOException("Espaço livre insuficiente para importar o jogo.");
                            out.write(buffer,0,read);
                        }
                    }
                    if(relative.equalsIgnoreCase("default.xex"))foundDefault=true;
                }
            }
        } catch(SecurityException e) {throw new IOException("Sem permissão para ler a pasta selecionada.",e);}
    }
    private void validate() throws IOException {
        File xex=new File(destination,"default.xex");
        if(!foundDefault||!xex.isFile()||xex.length()<24)throw new IOException("default.xex não foi encontrado na raiz da pasta.");
        byte[] header=new byte[1024*1024];int total=0;
        try(InputStream in=new FileInputStream(xex)) {
            while(total<header.length) {int n=in.read(header,total,header.length-total);if(n<0)break;total+=n;}
        }
        XexMetadata metadata=XexMetadata.parse(java.util.Arrays.copyOf(header,total));
        if(!metadata.xex2)throw new IOException("default.xex não possui cabeçalho XEX2 válido.");
        if(!"4541083B".equals(metadata.titleId))throw new IOException("Jogo incompatível. Title ID encontrado: "+(metadata.titleId.isEmpty()?"não identificado":metadata.titleId)+"; esperado: 4541083B (FIFA Street 3).");
    }
    private static void deleteTree(File f) throws IOException {
        if(!f.exists())return;File[] children=f.listFiles();
        if(children!=null)for(File child:children)deleteTree(child);
        if(!f.delete())throw new IOException("Não foi possível remover os dados temporários.");
    }
}
