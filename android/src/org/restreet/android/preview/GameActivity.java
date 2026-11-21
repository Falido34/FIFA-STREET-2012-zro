package org.restreet.android.preview;

import org.libsdl.app.SDLActivity;
import java.io.File;

public final class GameActivity extends SDLActivity {
    private TouchControls touch;
    private PerformanceOverlay performance;
    private android.widget.TextView finishEditing;
    @Override protected void onCreate(android.os.Bundle saved) {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try {
            android.system.Os.setenv("REX_NATIVE_LIBC_HOOKS","1",true);
            android.system.Os.setenv("REX_ADRENO_HOOK_DIR",getApplicationInfo().nativeLibraryDir,true);
            android.system.Os.setenv("REX_ADRENO_TMP_DIR",getFilesDir().getAbsolutePath(),true);
            java.io.File selected=new java.io.File(getFilesDir(),"gpu-drivers/active.txt");
            if(selected.isFile()) {
                try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.FileReader(selected))) {
                    String directory=reader.readLine(),library=reader.readLine();
                    if(directory!=null&&library!=null&&!directory.isEmpty()&&!library.isEmpty()) {
                        if(!directory.endsWith(java.io.File.separator))directory+=java.io.File.separator;
                        android.system.Os.setenv("REX_ADRENO_DRIVER_DIR",directory,true);
                        android.system.Os.setenv("REX_ADRENO_DRIVER_NAME",library,true);
                    } else {
                        android.system.Os.unsetenv("REX_ADRENO_DRIVER_DIR");
                        android.system.Os.unsetenv("REX_ADRENO_DRIVER_NAME");
                    }
                } catch(java.io.IOException error) {
                    android.util.Log.w("FifaAdreno","Invalid custom driver selection; using system Vulkan",error);
                    android.system.Os.unsetenv("REX_ADRENO_DRIVER_DIR");
                    android.system.Os.unsetenv("REX_ADRENO_DRIVER_NAME");
                }
            } else {
                android.system.Os.unsetenv("REX_ADRENO_DRIVER_DIR");
                android.system.Os.unsetenv("REX_ADRENO_DRIVER_NAME");
            }
        } catch(android.system.ErrnoException e) {throw new IllegalStateException(e);}
        super.onCreate(saved);
        android.view.View decor=getWindow().getDecorView();
        android.widget.FrameLayout overlay=(android.widget.FrameLayout)decor;
        setControls(getIntent().getIntExtra("controls",1));
            android.widget.ImageButton switcher=new android.widget.ImageButton(this);
            switcher.setImageResource(android.R.drawable.ic_menu_preferences);switcher.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFEAEDE7));
            switcher.setContentDescription("Controles: estilo, tamaño y transparencia");
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setShape(android.graphics.drawable.GradientDrawable.OVAL);background.setColor(0x55202523);switcher.setBackground(background);
            int target=Math.round(48*getResources().getDisplayMetrics().density);
            int inset=Math.round(14*getResources().getDisplayMetrics().density);switcher.setPadding(inset,inset,inset,inset);
            android.widget.FrameLayout.LayoutParams position=new android.widget.FrameLayout.LayoutParams(target,target,android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);
            position.topMargin=Math.round(8*getResources().getDisplayMetrics().density);
            switcher.setElevation(1000f);
            overlay.addView(switcher,position);
            switcher.setOnClickListener(v->{
                android.widget.PopupMenu menu=new android.widget.PopupMenu(this,switcher);
                for(int i=0;i<TouchControls.STYLES.length;i++)menu.getMenu().add(0,i,i,TouchControls.STYLES[i]);
                menu.getMenu().add(0,3,3,"Tamanho e transparência");
                menu.getMenu().add(0,4,4,"Editar posição dos controles");
                menu.getMenu().add(0,5,5,"Mostrar/ocultar FPS, CPU e GPU");
                menu.setOnMenuItemClickListener(item->{
                    if(item.getItemId()==3)showControlSettings();
                    else if(item.getItemId()==4)startControlEditing(overlay);
                    else if(item.getItemId()==5)togglePerformanceOverlay(overlay);
                    else {setControls(item.getItemId());switcher.bringToFront();}
                    return true;
                });
                menu.show();
            });
    }
    private void setControls(int style) {
        if(touch!=null && touch.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)touch.getParent()).removeView(touch);
        touch=new TouchControls(this,style);
        touch.setElevation(900f);
        android.widget.FrameLayout overlay=(android.widget.FrameLayout)getWindow().getDecorView();
        overlay.addView(touch,new android.widget.FrameLayout.LayoutParams(-1,-1));
        touch.bringToFront();
    }
    private void startControlEditing(android.widget.FrameLayout overlay) {
        if(touch==null)return;
        touch.setEditMode(true);
        if(finishEditing!=null)overlay.removeView(finishEditing);
        finishEditing=new android.widget.TextView(this);
        finishEditing.setText("SALVAR LAYOUT");finishEditing.setTextColor(0xFFFFFFFF);
        finishEditing.setTextSize(14);finishEditing.setGravity(android.view.Gravity.CENTER);
        finishEditing.setPadding(24,12,24,12);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xDD246B45);bg.setCornerRadius(24*getResources().getDisplayMetrics().density);
        finishEditing.setBackground(bg);finishEditing.setElevation(1200f);
        android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin=Math.round(18*getResources().getDisplayMetrics().density);
        overlay.addView(finishEditing,lp);
        finishEditing.setOnClickListener(v->{touch.setEditMode(false);overlay.removeView(finishEditing);finishEditing=null;});
        finishEditing.bringToFront();
    }
    private void togglePerformanceOverlay(android.widget.FrameLayout overlay) {
        if(performance!=null) {overlay.removeView(performance);performance.stop();performance=null;return;}
        performance=new PerformanceOverlay(this);
        android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP|android.view.Gravity.RIGHT);
        lp.topMargin=Math.round(58*getResources().getDisplayMetrics().density);
        lp.rightMargin=Math.round(10*getResources().getDisplayMetrics().density);
        overlay.addView(performance,lp);performance.start();performance.bringToFront();
    }
    private void showControlSettings() {
        touch.releaseAll();
        android.content.SharedPreferences prefs=TouchControls.options(this);
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        int gap=Math.round(20*getResources().getDisplayMetrics().density);panel.setPadding(gap,gap,gap,0);
        slider(panel,"Tamaño",50,110,prefs.getInt("size",70),value->{prefs.edit().putInt("size",value).apply();touch.rebuild();});
        slider(panel,"Transparencia",0,80,100-prefs.getInt("opacity",65),value->{prefs.edit().putInt("opacity",100-value).apply();touch.rebuild();});
        toggle(panel,"Mostrar cruceta",prefs.getBoolean("dpad",false),checked->{prefs.edit().putBoolean("dpad",checked).apply();touch.rebuild();});
        String stickKey="right_stick_"+touch.getStyle();
        toggle(panel,"Mostrar stick derecho (regates)",prefs.getBoolean(stickKey,touch.getStyle()==1),checked->{prefs.edit().putBoolean(stickKey,checked).apply();touch.rebuild();});
        toggle(panel,"Mostrar botones avanzados (LB, RB, L3, R3)",prefs.getBoolean("advanced",false),checked->{prefs.edit().putBoolean("advanced",checked).apply();touch.rebuild();});
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
        new android.app.AlertDialog.Builder(this).setTitle("Controles táctiles").setView(scroll).setPositiveButton("LISTO",null)
            .setNeutralButton("RESTABLECER",(dialog,which)->{prefs.edit().clear().apply();touch.rebuild();}).show();
    }
    private interface IntChange {void changed(int value);}
    private interface BoolChange {void changed(boolean value);}
    private void slider(android.widget.LinearLayout parent,String name,int min,int max,int value,IntChange change) {
        android.widget.TextView label=new android.widget.TextView(this);label.setText(name+": "+value+" %");parent.addView(label);
        android.widget.SeekBar slider=new android.widget.SeekBar(this);slider.setContentDescription(name);slider.setMax(max-min);slider.setProgress(value-min);
        parent.addView(slider,new android.widget.LinearLayout.LayoutParams(-1,Math.round(48*getResources().getDisplayMetrics().density)));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){if(fromUser){int selected=progress+min;label.setText(name+": "+selected+" %");change.changed(selected);}}
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar){touch.releaseAll();}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
    }
    private void toggle(android.widget.LinearLayout parent,String name,boolean value,BoolChange change) {
        android.widget.CheckBox box=new android.widget.CheckBox(this);box.setText(name);box.setChecked(value);parent.addView(box);
        box.setOnCheckedChangeListener((button,checked)->change.changed(checked));
    }
    @Override protected void onPause() {if(touch!=null)touch.releaseAll();if(performance!=null)performance.stop();super.onPause();}
    @Override protected void onResume() {setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);super.onResume();if(performance!=null)performance.start();}
    private boolean isFifa3() { return "fifastreet3".equals(getIntent().getStringExtra("game")); }
    @Override protected String[] getLibraries() {return isFifa3() ? new String[]{"c++_shared","rexruntime","rexgpu-xenos","touchcontrols","fifastreet3"} : new String[]{"c++_shared","rexruntime","touchcontrols","fifastreet"};}
    @Override protected String[] getArguments() {
        GpuProfile gpu=GpuProfile.fromName(getIntent().getStringExtra("gpu_profile"));
        return new String[]{
            "--game_data_root",new File(getFilesDir(),isFifa3()?"FifaStreet3Data":"GameData").getAbsolutePath(),
            "--user_data_root",new File(getFilesDir(),"UserData").getAbsolutePath(),
            "--video_mode_width=1280","--video_mode_height=720",
            gpu.vertexStoresArgument(),"--no-vulkan_require_geometry_shader","--no-vulkan_require_fill_mode_non_solid",
            "--async_shader_compilation=false","--occlusion_query_enable=true","--readback_memexport=true",
            "--vulkan_diagnostic_tiny_render=false","--vulkan_optimize_spirv=false",
            "--gamma_render_target_as_unorm16=true",
            "--android_thread_placement=1","--vulkan_cache_cpu_uploads=true",
            "--vulkan_hoist_shared_read_barriers=true","--vulkan_hoist_cpu_uploads=false",
            "--vulkan_preload_draw_buffers=true","--vulkan_batch_disjoint_shared_writes=true",
            "--vulkan_persistent_pipeline_cache=true","--vulkan_trim_render_area=true",
            "--vulkan_submit_on_primary_buffer_end=true",
            "--log_frame_stats","--log_level=info",
            "--log_file",new File(getFilesDir(),"restreet.log").getAbsolutePath()
        };
    }
}
