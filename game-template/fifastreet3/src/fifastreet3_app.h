#pragma once

#include <memory>
#include <rex/rex_app.h>
#include <rex/filesystem.h>
#include <rex/ui/window.h>

class FifaStreet3App : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<FifaStreet3App>(
        new FifaStreet3App(ctx, "fifastreet3", PPCImageConfig));
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    if (config.gpu_plugin.empty()) config.gpu_plugin = "xenos";
  }

  void OnConfigurePaths(rex::PathConfig& paths) override {
#if defined(__ANDROID__)
    paths.config_path = paths.user_data_root / "fifastreet3.toml";
#endif
    if (paths.game_data_root.empty()) {
      const auto folder = rex::filesystem::GetExecutableFolder();
      for (const auto& candidate : {folder, folder / "GameData",
                                    folder.parent_path() / "GameData",
                                    folder.parent_path()}) {
        std::error_code error;
        if (std::filesystem::is_regular_file(candidate / "default.xex", error)) {
          paths.game_data_root = candidate;
          break;
        }
      }
    }
  }

  void OnCreateDialogs(rex::ui::ImGuiDrawer*) override {
#if defined(__ANDROID__)
    window()->SetTitle("FIFA Street 3 Android");
#else
    window()->SetTitle("FIFA Street 3");
#endif
  }
};
