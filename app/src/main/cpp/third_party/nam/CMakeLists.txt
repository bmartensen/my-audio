# Neural Amp Modeler inference (NeuralAmpModelerCore, MIT) with Eigen (MPL-2.0) and nlohmann/json (MIT);
# see README.md for provenance. Built as its own optimised object library in C++20, float samples and
# the A2 fast path, shared by Android and desktop.
file(GLOB NAM_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/NAM/*.cpp ${CMAKE_CURRENT_SOURCE_DIR}/NAM/wavenet/*.cpp)
# An object library, not a static one: architectures register themselves in static initialisers, which a
# linker would drop from a static archive because nothing references them.
add_library(nam OBJECT ${NAM_SOURCES})
target_compile_features(nam PUBLIC cxx_std_20)
target_compile_definitions(nam PUBLIC NAM_SAMPLE_FLOAT NAM_ENABLE_A2_FAST)
target_include_directories(nam PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/NAM
        ${CMAKE_CURRENT_SOURCE_DIR}/Dependencies/nlohmann)
target_include_directories(nam SYSTEM PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/Dependencies/eigen)
set_target_properties(nam PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(MSVC)
    target_compile_options(nam PRIVATE /W0 /O2 /fp:fast /utf-8 /bigobj)
    target_compile_definitions(nam PRIVATE _USE_MATH_DEFINES NOMINMAX _CRT_SECURE_NO_WARNINGS)
else()
    target_compile_options(nam PRIVATE -w -O3 -ffast-math)
endif()
