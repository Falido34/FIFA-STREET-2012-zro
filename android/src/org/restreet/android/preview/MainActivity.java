package org.restreet.android.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_ISO = 42;
    private static final int PICK_FOLDER = 43;
    private static final int PICK_ADRENO_DRIVER = 44;\n    private static final int PICK_FIFA3_FOLDER = 45;
    private TextView status;
    private ProgressBar progress;
    private Button importButton, folderButton, playButton, cancelButton, driverButton, systemDriverButton;
    private Spinner gpu, controls;
    private TextView driverStatus;
    private volatile boolean cancelled;
    private volatile boolean importing;
    private Thread worker;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private File data() { return new File(getFilesDir(), "GameData"); }
    private boolean gameReady() {
        return new File(data(), ".restreet-import-complete").isFile()
                && new File(data(), "default.xex").isFile()
                && new File(data(), "fifadllzf.xex.dll").isFile()
                && new File(data(), "dlc/dlc_FootballCompEng/dlc/FootballCompEng/FootballCompEngzf.xex.dll").isFile();
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF121818); getWindow().setNavigationBarColor(0xFF121818);
        // Dark Moon portrait launcher: use the bundled cover as a full-screen backdrop.
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF08090D);
        ImageView cover = new ImageView(this);
        cover.setImageResource(getResources().getIdentifier("cover", "drawable", getPackageName()));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setContentDescription("Fundo FIFA Street");
        root.addView(cover, new android.widget.FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this); shade.setBackgroundColor(0xCC08090D);
        root.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(28),dp(22),dp(28));
        scroll.addView(content);
        root.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        TextView title = text("FIFA STREET", 34, 0xFFF4EEE0); title.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); content.addView(title);
        TextView edition = text("DARK MOON  /  ANDROID", 12, 0xFFE05252); edition.setTypeface(Typeface.DEFAULT_BOLD); content.addView(edition);
        content.addView(text("0.1 · VERTICAL EDITION", 13, 0xFFD6B477));

        TextView guide = text("Selecione a ISO compatível ou importe a pasta do jogo (Xbox 360 · Title ID 4541097B · Media ID 78AA9072).",15,0xFFDEE4DF);
        guide.setPadding(0,dp(14),0,dp(8)); content.addView(guide);
        status = text("",14,0xFFDEE4DF); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(status);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setVisibility(View.GONE); content.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        importButton = new Button(this); importButton.setText("IMPORTAR Y EXTRAER ISO"); importButton.setMinHeight(dp(48));
        importButton.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(picker,PICK_ISO); }
            catch (android.content.ActivityNotFoundException e) { status.setText("No hay un selector de archivos disponible en este teléfono."); }
        }); content.addView(importButton);
        folderButton = new Button(this); folderButton.setText("IMPORTAR PASTA DO FIFA STREET 2012"); folderButton.setMinHeight(dp(48));
        folderButton.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(picker, PICK_FOLDER); }
            catch (android.content.ActivityNotFoundException e) { status.setText("Não há um seletor de pastas disponível neste telefone."); }
        }); content.addView(folderButton);
        Button fifa3Button = new Button(this); fifa3Button.setText("ANALISAR PASTA DO FIFA STREET 3"); fifa3Button.setMinHeight(dp(48));
        fifa3Button.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(picker, PICK_FIFA3_FOLDER); }
            catch (android.content.ActivityNotFoundException e) { status.setText("Não há seletor de pastas disponível neste telefone."); }
        }); content.addView(fifa3Button);
        cancelButton = new Button(this); cancelButton.setText("CANCELAR EXTRACCIÓN"); cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> { cancelled=true; status.setText("Cancelando y retirando la extracción incompleta…"); }); content.addView(cancelButton);
        content.addView(label("Compatibilidad de GPU"));
        gpu = new Spinner(this); ArrayAdapter<GpuProfile> profiles = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,GpuProfile.values());
        profiles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);gpu.setAdapter(profiles);gpu.setContentDescription("Compatibilidad de GPU");
        gpu.setSelection(GpuProfile.fromName(getPreferences(MODE_PRIVATE).getString("gpu_profile","MALI")).ordinal());content.addView(gpu,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView runtimeInfo = text("FPS / CPU: disponíveis durante o jogo · GPU e driver selecionados abaixo", 11, 0xFFB8B2B0);
        runtimeInfo.setPadding(0,dp(4),0,dp(4)); content.addView(runtimeInfo);
        content.addView(label("Driver Vulkan Adreno (opcional)"));
        driverStatus=text("",13,0xFFBDCFC5);driverStatus.setText(AdrenoDriverManager.selectedLabel(this));content.addView(driverStatus);
        driverButton=new Button(this);driverButton.setText("IMPORTAR DRIVER ADRENO (ZIP)");driverButton.setMinHeight(dp(48));
        driverButton.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT<28){status.setText("Adreno Tools requer Android 9 ou superior.");return;}
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
            try{startActivityForResult(picker,PICK_ADRENO_DRIVER);}
            catch(android.content.ActivityNotFoundException e){status.setText("Não há seletor de arquivos disponível.");}
        });content.addView(driverButton);
        systemDriverButton=new Button(this);systemDriverButton.setText("USAR DRIVER ORIGINAL DO SISTEMA");
        systemDriverButton.setOnClickListener(v->{
            try{AdrenoDriverManager.useSystem(this);driverStatus.setText(AdrenoDriverManager.selectedLabel(this));status.setText("Driver do sistema selecionado. A alteração vale ao abrir o jogo novamente.");}
            catch(IOException e){status.setText("Não foi possível trocar o driver: "+e.getMessage());}
        });content.addView(systemDriverButton);
        content.addView(label("Estilo de controles"));
        controls = new Spinner(this);ArrayAdapter<String> styles = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,TouchControls.STYLES);
        styles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);controls.setAdapter(styles);controls.setContentDescription("Estilo de controles táctiles");
        controls.setSelection(Math.max(0,Math.min(2,getPreferences(MODE_PRIVATE).getInt("controls",1))));content.addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(text("Mando Bluetooth ou USB: pode usar junto com os controles táteis, que continuam visíveis.",13,0xFFBDCFC5));
        playButton=new Button(this);playButton.setText("JOGAR");playButton.setTextColor(Color.WHITE);playButton.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{0xFF38444D,0xFF8F2028,0xFFB52B35}));playButton.setMinHeight(dp(56));
        LinearLayout.LayoutParams playLayout=new LinearLayout.LayoutParams(-1,dp(52));playLayout.topMargin=dp(16);content.addView(playButton,playLayout);
        playButton.setOnClickListener(v -> {
            if (!gameReady() || importing) return;
            GpuProfile selected=(GpuProfile)gpu.getSelectedItem();int style=controls.getSelectedItemPosition();
            getPreferences(MODE_PRIVATE).edit().putString("gpu_profile",selected.name()).putInt("controls",style).apply();
            startActivity(new Intent(this,GameActivity.class).putExtra("gpu_profile",selected.name()).putExtra("controls",style));
        });
        content.addView(text("Versión experimental. Hay fallos gráficos y rendimiento limitado. Mali y Adreno no tienen compatibilidad completa.\nISO compatible: Title ID 4541097B · Media ID 78AA9072. No incluye el juego.",12,0xFFBDCFC5));
        setContentView(root);refresh();
    }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t; }
    private TextView label(String value) { TextView t=text(value,15,0xFFF4EEE0);t.setPadding(0,dp(16),0,dp(2));return t; }
    private void refresh() {
        boolean ready=gameReady();playButton.setEnabled(ready&&!importing);
        if(driverStatus!=null)driverStatus.setText(AdrenoDriverManager.selectedLabel(this));importButton.setEnabled(!importing&&!data().exists());
        folderButton.setEnabled(!importing&&!data().exists());
        gpu.setEnabled(!importing);controls.setEnabled(!importing);
        if(driverButton!=null)driverButton.setEnabled(!importing&&Build.VERSION.SDK_INT>=28);
        if(systemDriverButton!=null)systemDriverButton.setEnabled(!importing);
        if (!importing) status.setText(ready?"Juego importado.":data().exists()?"GameData está incompleto. Se conservó sin sobrescribir; revisa el almacenamiento de la app.":"");
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null||importing)return;
        final Uri uri=intent.getData();
        if(request==PICK_ADRENO_DRIVER) {
            cancelled=false;importing=true;refresh();
            progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);cancelButton.setVisibility(View.GONE);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            worker=new Thread(()->{
                String message;boolean installedOk=false;
                try { String installed=AdrenoDriverManager.importZip(this,uri);getPreferences(MODE_PRIVATE).edit().putString("gpu_profile",GpuProfile.ADRENO.name()).apply();installedOk=true;message="Driver importado: "+installed+". Perfil Adreno selecionado; feche e abra o jogo novamente para carregar."; }
                catch(Exception e){message="Falha ao importar driver: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
                final String importMessage=message;final boolean selectAdreno=installedOk;
                runOnUiThread(()->{importing=false;if(isDestroyed())return;if(selectAdreno)gpu.setSelection(GpuProfile.ADRENO.ordinal());progress.setVisibility(View.GONE);cancelButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);refresh();status.setText(importMessage);});
            },"Adreno driver importer");worker.start();
            return;
        }
        if(request==PICK_FIFA3_FOLDER) {
            try {
                getContentResolver().takePersistableUriPermission(uri, intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            cancelled=false;importing=true;refresh();
            progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);cancelButton.setVisibility(View.VISIBLE);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            worker=new Thread(() -> {
                String report;
                try {
                    runOnUiThread(() -> status.setText("Analisando os executáveis do FIFA Street 3…"));
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Análise cancelada.");
                    report=GameFolderInspector.inspect(this,uri);
                } catch(Exception e) {
                    report="Análise da pasta falhou: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
                }
                final String resultText=report;
                runOnUiThread(() -> {
                    importing=false;
                    if(isDestroyed())return;
                    progress.setVisibility(View.GONE);cancelButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    refresh();status.setText(resultText);
                });
            },"FIFA Street 3 folder inspector");worker.start();
            return;
        }
        if(request==PICK_FOLDER) {
            cancelled=false;importing=true;refresh();
            progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);cancelButton.setVisibility(View.VISIBLE);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            worker=new Thread(() -> importGameFolder(uri),"Game folder importer");worker.start();
            return;
        }
        if(request!=PICK_ISO)return;
        cancelled=false;importing=true;refresh();
        progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);cancelButton.setVisibility(View.VISIBLE);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker=new Thread(() -> importIso(uri),"ISO extractor");worker.start();
    }
    private void importGameFolder(Uri uri) {
        File staging=new File(getFilesDir(),"folder-stage-"+java.util.UUID.randomUUID());
        String result;
        try {
            runOnUiThread(() -> status.setText("Lendo e verificando a pasta do jogo…"));
            GameFolderImporter.importFolder(this,uri,staging,message -> {
                if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Importação cancelada.");
                runOnUiThread(() -> {if(!isDestroyed())status.setText(message);});
            });
            if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Importação cancelada.");
            if(data().exists()||!staging.renameTo(data()))throw new IOException("Não foi possível salvar GameData. Os dados existentes foram preservados.");
            result="Pasta importada e verificada. Já pode jogar.";
        } catch(Exception e) {
            result="Importação da pasta falhou: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            if(staging.exists())try { removeFolderStaging(staging); } catch(IOException cleanup) { android.util.Log.w("FifaFolder","Falha ao limpar pasta temporária",cleanup); }
        }
        final String message=result;
        runOnUiThread(() -> {
            importing=false;
            if(isDestroyed())return;
            progress.setVisibility(View.GONE);cancelButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refresh();status.setText(message);
        });
    }
    private void removeFolderStaging(File stage) throws IOException {
        File root=getFilesDir().getCanonicalFile();File target=stage.getCanonicalFile();
        if(!root.equals(target.getParentFile())||!target.getName().startsWith("folder-stage-"))return;
        removeOwned(target,root.getPath()+File.separator);
    }
    private void importIso(Uri uri) {
        File staging=null, spool=null;
        String result;
        try {
            // A provider may expose a pipe. Cache only that case; seekable local ISOs need no duplicate.
            ParcelFileDescriptor descriptor=getContentResolver().openFileDescriptor(uri,"r");
            if(descriptor==null)throw new IOException("No se pudo abrir la ISO.");
            try(ParcelFileDescriptor.AutoCloseInputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                FileChannel channel=stream.getChannel();boolean seekable;
                try { seekable=channel.size()>0;channel.position(0); } catch(IOException e) { seekable=false; }
                if(seekable) {
                    staging=extract(channel);
                } else {
                    spool=File.createTempFile("iso-import-",".iso",getCacheDir());
                    try(FileOutputStream copy=new FileOutputStream(spool)) {
                        byte[] chunk=new byte[1024*1024];int count;
                        while((count=stream.read(chunk))!=-1) {
                            checkCancel();
                            if(new StatFs(getFilesDir().getPath()).getAvailableBytes()<count+64L*1024*1024)throw new IOException("No hay espacio para copiar la ISO. Selecciona un archivo guardado en el teléfono.");
                            copy.write(chunk,0,count);
                        }
                    }
                    try(FileInputStream local=new FileInputStream(spool)) { staging=extract(local.getChannel()); }
                }
            }
            checkCancel();
            if(data().exists()||!staging.renameTo(data()))throw new IOException("No se pudo guardar GameData. Los datos anteriores se conservan.");
            staging=null;result="Juego importado y verificado. Ya puedes jugar.";
        } catch(Exception e) { result="Importación detenida: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()); }
        finally {
            if(staging!=null)removeStaging(staging);
            if(spool!=null&&!spool.delete())android.util.Log.w("FifaIso","No se pudo retirar la copia temporal de ISO");
        }
        final String message=result;
        runOnUiThread(() -> {
            importing=false;
            if(isDestroyed())return;
            progress.setVisibility(View.GONE);cancelButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refresh();status.setText(message);
        });
    }
    private void checkCancel() throws IOException { if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelado. Tu ISO original no se modificó."); }
    private File extract(FileChannel channel) throws IOException {
        runOnUiThread(() -> status.setText("Verificando la ISO y su edición…"));
        IsoExtractor image=new IsoExtractor(channel);List<IsoExtractor.Entry> entries=image.inventory();checkCancel();image.validateGame(entries);checkCancel();
        long total=IsoExtractor.totalBytes(entries);
        if(new StatFs(getFilesDir().getPath()).getAvailableBytes()<total+64L*1024*1024)throw new IOException("No hay espacio. Necesitas "+((total+1048575)/1048576)+" MiB libres para extraer el juego.");
        File stage=new File(getFilesDir(),"iso-stage-"+java.util.UUID.randomUUID());
        final int[] last={-1};
        try {
            image.extract(entries,stage,(done,all) -> {
                checkCancel();int percent=all==0?100:(int)(done*100/all);
                if(percent!=last[0]) {last[0]=percent;runOnUiThread(() -> {if(!isDestroyed()){progress.setIndeterminate(false);progress.setProgress(percent);status.setText("Extrayendo ISO · "+percent+" %");}});}
            });
            try(FileOutputStream marker=new FileOutputStream(new File(stage,".restreet-import-complete"))) {marker.write("4541097B/78AA9072".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            return stage;
        } catch(IOException|RuntimeException error) { removeStaging(stage);throw error; }
    }
    private void removeStaging(File stage) {
        try {
            File root=getFilesDir().getCanonicalFile();File target=stage.getCanonicalFile();
            if(!root.equals(target.getParentFile())||!target.getName().startsWith("iso-stage-"))return;
            removeOwned(target,root.getPath()+File.separator);
        } catch(IOException e) {android.util.Log.w("FifaIso","No se pudo retirar la extracción incompleta",e);}
    }
    private void removeOwned(File file,String prefix) throws IOException {
        if(!file.getCanonicalPath().startsWith(prefix))throw new IOException("Ruta temporal fuera de la app.");
        File[] children=file.listFiles();if(children!=null)for(File child:children)removeOwned(child,prefix);
        if(file.exists()&&!file.delete())throw new IOException("No se pudo retirar un archivo temporal.");
    }
    @Override protected void onDestroy() {cancelled=true;if(worker!=null)worker.interrupt();super.onDestroy();}
}
