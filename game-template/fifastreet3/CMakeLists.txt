cmake_minimum_required(VERSION 3.25)
project(fifastreet3 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Keep the FIFA Street 3 project isolated from the FIFA Street 2012 target.
include(generated/rexglue.cmake)
if(NOT ANDROID)
    message(FATAL_ERROR "FIFA Street 3 native target requires Android ARM64")
endif()

add_library(fifastreet3 SHARED src/main.cpp)
rexglue_setup_target(fifastreet3 GPU_PLUGINS xenos)
target_include_directories(fifastreet3 PRIVATE
    src
    # ReXGlue public app headers include imgui.h, but rex::runtime does not
    # propagate the imgui object target's include path to downstream apps.
    "${REXSDK_DIR}/thirdparty/imgui"
)
target_compile_options(fifastreet3 PRIVATE -O2)
if(TARGET fifastreet3_recomp)
    target_compile_options(fifastreet3_recomp PRIVATE -O2)
endif()
set_source_files_properties(
    "${CMAKE_CURRENT_SOURCE_DIR}/generated/default/fifastreet3_register.cpp"
    PROPERTIES COMPILE_OPTIONS "-O0" SKIP_PRECOMPILE_HEADERS ON)
