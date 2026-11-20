package org.restreet.android.preview;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AdrenoDriverManager {
    private static final long MAX_TOTAL = 512L * 1024 * 1024;
    private static final long MAX_ENTRY = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 512;
    private AdrenoDriverManager() {}

    static String importZip(Context context, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT < 28) throw new IOException("Adreno Tools requer Android 9 ou superior.");
        File root = new File(context.getFilesDir(), "gpu-drivers");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Não foi possível criar a pasta de drivers.");
        File stage = new File(root, ".import-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Não foi possível preparar a importação.");
        long total = 0; int count = 0; JSONObject meta = null; String chosen = null;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("Não foi possível abrir o ZIP.");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                byte[] buffer = new byte[64 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES) throw new IOException("ZIP excede o limite de 512 arquivos.");
                    String path = entry.getName().replace('\\', '/');
                    if (entry.isDirectory()) { zip.closeEntry(); continue; }
                    if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) throw new IOException("Caminho inválido dentro do ZIP.");
                    String[] parts = path.split("/");
                    for (String part : parts) if (part.equals("..") || part.equals(".")) throw new IOException("ZIP contém caminho inseguro.");
                    String base = parts[parts.length - 1];
                    if (base.equalsIgnoreCase("meta.json")) {
                        ByteArrayOutputStream data = new ByteArrayOutputStream();
                        int n; while ((n=zip.read(buffer))!=-1) { total+=n; if(total>MAX_TOTAL || data.size()+n>1024*1024) throw new IOException("Metadados excedem o limite."); data.write(buffer,0,n); }
                        try { meta = new JSONObject(new String(data.toByteArray(), StandardCharsets.UTF_8)); }
                        catch (Exception e) { throw new IOException("meta.json inválido.",e); }
                    } else if (base.toLowerCase(Locale.ROOT).endsWith(".so")) {
                        if (base.length()>180 || base.contains(":")) throw new IOException("Nome de biblioteca inválido.");
                        File out = new File(stage, base);
                        if (out.exists()) throw new IOException("O ZIP contém bibliotecas duplicadas: "+base);
                        try (FileOutputStream file = new FileOutputStream(out)) {
                            int n; long entryBytes=0;
                            while ((n=zip.read(buffer))!=-1) {
                                entryBytes+=n; total+=n;
                                if(entryBytes>MAX_ENTRY || total>MAX_TOTAL) throw new IOException("Driver excede o limite de 512 MiB.");
                                file.write(buffer,0,n);
                            }
                        }
                    } else {
                        int n;
                        while((n=zip.read(buffer))!=-1) {
                            total+=n;
                            if(total>MAX_TOTAL)throw new IOException("ZIP excede o limite de 512 MiB descompactados.");
                        }
                    }
                    zip.closeEntry();
                }
            }
            if(meta!=null&&meta.optInt("minApi",28)>Build.VERSION.SDK_INT)
                throw new IOException("Este driver exige Android "+meta.optInt("minApi")+".");
            String library = meta==null ? "" : meta.optString("libraryName",meta.optString("library",""));
            if (!library.isEmpty()) {
                File candidate = new File(stage, new File(library).getName());
                if (candidate.isFile()) chosen=candidate.getName();
            }
            if (chosen==null) {
                String[] preferred={"libvulkan_freedreno.so","libvulkan_turnip.so","libvulkan.so"};
                for(String name:preferred) if(new File(stage,name).isFile()){chosen=name;break;}
            }
            if(chosen==null) {
                File[] libs=stage.listFiles((dir,name)->name.startsWith("vulkan.")&&name.endsWith(".so"));
                if(libs!=null&&libs.length==1)chosen=libs[0].getName();
            }
            if(chosen==null) throw new IOException("ZIP não contém um driver Vulkan Adreno reconhecível.");
            if(!isArm64Elf(new File(stage,chosen))) throw new IOException("O driver selecionado não é uma biblioteca ELF ARM64 válida.");
            String label=meta==null?"Driver Adreno":meta.optString("name","Driver Adreno");
            String version=meta==null?"":meta.optString("driverVersion","");
            label=label.replaceAll("[\\r\\n\\t]"," ").trim();
            if(label.isEmpty())label="Driver Adreno";
            String id=UUID.randomUUID().toString();
            File installed=new File(root,id);
            if(!stage.renameTo(installed))throw new IOException("Não foi possível instalar o driver no armazenamento privado.");
            File selectedTmp=new File(root,"active.tmp");
            try(FileOutputStream out=new FileOutputStream(selectedTmp)) {
                String record=installed.getAbsolutePath()+"\n"+chosen+"\n"+label+"\n"+version;
                out.write(record.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            File active=new File(root,"active.txt");
            if(active.exists()&&!active.delete())throw new IOException("Não foi possível atualizar a seleção do driver.");
            if(!selectedTmp.renameTo(active))throw new IOException("Não foi possível salvar a seleção do driver.");
            return label+(version.isEmpty()?"":" · "+version);
        } catch (IOException|RuntimeException e) {
            deleteTree(stage);
            throw e;
        }
    }
    private static boolean isArm64Elf(File file) throws IOException {
        if(file.length()<64) return false;
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))) {
            byte[] h=new byte[20]; if(in.read(h)!=h.length)return false;
            return (h[0]&255)==0x7f&&h[1]=='E'&&h[2]=='L'&&h[3]=='F'&&h[4]==2&&h[5]==1
                    && (h[18]&255)==183 && h[19]==0;
        }
    }
    static boolean isInstalled(Context context) { return new File(new File(context.getFilesDir(),"gpu-drivers"),"active.txt").isFile(); }
    static String selectedLabel(Context context) {
        File f=new File(new File(context.getFilesDir(),"gpu-drivers"),"active.txt");
        if(!f.isFile())return "Driver do sistema";
        try(BufferedReader r=new BufferedReader(new FileReader(f))) {
            r.readLine();r.readLine();String name=r.readLine();String version=r.readLine();
            return (name==null?"Driver personalizado":name)+(version==null||version.isEmpty()?"":" · "+version);
        } catch(IOException e){return "Driver personalizado (seleção inválida)";}
    }
    static void useSystem(Context context) throws IOException {
        File root=new File(context.getFilesDir(),"gpu-drivers");
        File active=new File(root,"active.txt");
        if(active.exists()&&!active.delete())throw new IOException("Não foi possível remover a seleção do driver.");
    }
    static void deleteTree(File file) {
        File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);
        if(file.exists())file.delete();
    }
}
