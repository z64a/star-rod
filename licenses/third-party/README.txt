Third-party notices
===================

The library list is generated from runtimeClasspath. The dependency license
report comes from the existing Gradle licenseReport task. Notices supplied in
dependency jars are preserved separately before the jars are merged.

These supplemental texts cover libraries whose jars or Maven metadata omit
complete license texts:

MiGLayout 11.3
  BSD license text carried over from Star Rod Classic.
  Upstream: https://github.com/mikaelgrev/miglayout/tree/v11.3

SLF4J API 1.7.30 (transitive dependency of PNGJ)
  MIT license text carried over from Star Rod Classic.
  Upstream: https://www.slf4j.org/license.html

LWJGL 3.3.3 and its bundled native libraries
  Upstream: https://github.com/LWJGL/lwjgl3/tree/3.3.3
  LWJGL-3.3.3.txt: LICENSE.md
  LWJGL-libffi.txt: modules/lwjgl/core/libffi_license.txt
  LWJGL-liburing.txt: modules/lwjgl/core/liburing_license.txt (Linux)
  LWJGL-OpenGL.txt: modules/lwjgl/opengl/khronos_license.txt
  LWJGL-GLFW.txt: modules/lwjgl/glfw/glfw_license.txt
  LWJGL-tinyfd.txt: modules/lwjgl/tinyfd/tinyfd_license.txt
  LWJGL-assimp.txt: modules/lwjgl/assimp/assimp_license.txt

Update supplemental notices when updating these dependencies.
Splash photos and fonts have separate credits and licenses beside their assets.
The Java runtime supplies its own notices in its legal/ directory.
