from pathlib import Path
import unittest

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "game-template").is_dir())


class FifaStreet3ProfileTest(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_profile_uses_separate_project_and_target(self):
        cmake = self.read("game-template/fifastreet3/CMakeLists.txt")
        self.assertIn("project(fifastreet3 LANGUAGES CXX)", cmake)
        self.assertIn("add_library(fifastreet3 SHARED src/main.cpp)", cmake)
        self.assertIn("rexglue_setup_target(fifastreet3 GPU_PLUGINS xenos)", cmake)
        self.assertNotIn("fifadllzf", cmake)
        self.assertNotIn("FootballCompEngzf", cmake)

    def test_generated_integration_matches_fifa_street_3_names(self):
        cmake = self.read("game-template/fifastreet3/generated/rexglue.cmake")
        self.assertIn("fifastreet3_codegen", cmake)
        self.assertIn("fifastreet3_manifest.toml", cmake)
        self.assertIn("fifastreet3_pch.h", cmake)
        self.assertNotIn("fifastreet_codegen", cmake)
        self.assertNotIn("fifastreet_manifest.toml", cmake)

    def test_entrypoint_uses_its_own_generated_header_and_app(self):
        main = self.read("game-template/fifastreet3/src/main.cpp")
        app = self.read("game-template/fifastreet3/src/fifastreet3_app.h")
        self.assertIn("fifastreet3_init.h", main)
        self.assertIn("FifaStreet3App::Create", main)
        self.assertIn('"fifastreet3"', app)
        self.assertIn("FIFA Street 3 Android", app)

    def test_build_script_stages_and_builds_separate_target(self):
        script = self.read("android/BuildFifaStreet3.ps1")
        self.assertIn("GenerateFifaStreet3.ps1", script)
        self.assertIn("game-template/fifastreet3", script)
        self.assertIn("--target fifastreet3", script)
        self.assertIn("libfifastreet3.so", script)

    def test_build_script_stages_template_before_game_specific_codegen(self):
        script = self.read("android/BuildFifaStreet3.ps1")
        stage = script.index("Copy-Item -Path (Join-Path $template '*')")
        codegen = script.index("GenerateFifaStreet3.ps1")
        self.assertLess(stage, codegen)
        self.assertIn("would overwrite it", script)


if __name__ == "__main__":
    unittest.main()
