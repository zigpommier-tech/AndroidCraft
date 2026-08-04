cmake_minimum_required(VERSION 3.22.1)
project(sdjni CXX C)

# Build only the core stable-diffusion library — no CLI examples, no WebP/WebM I/O,
# CPU backend only (no CUDA/Vulkan/OpenCL on this target).
set(SD_BUILD_EXAMPLES     OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS  OFF CACHE BOOL "" FORCE)
set(SD_WEBP               OFF CACHE BOOL "" FORCE)
set(SD_WEBM               OFF CACHE BOOL "" FORCE)

# ggml: cross-compiling for Android, so disable host-native tuning; NEON is enabled
# automatically for arm64. Keep OpenMP off for a self-contained library.
set(GGML_NATIVE   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP   OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(stable-diffusion.cpp)

add_library(sdjni SHARED sdjni.cpp)
target_compile_features(sdjni PRIVATE cxx_std_17)

find_library(log-lib log)
# stable-diffusion PUBLIC-exports its include dirs, so sdjni sees stable-diffusion.h.
target_link_libraries(sdjni PRIVATE stable-diffusion ${log-lib})
