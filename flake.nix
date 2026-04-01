{
  description = "Excavate; area mining enchantment mod for NeoForge 1.21.1";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};

      vineflower = pkgs.stdenv.mkDerivation {
        pname = "vineflower";
        version = "1.11.2";

        src = pkgs.fetchurl {
          url = "https://github.com/Vineflower/vineflower/releases/download/1.11.2/vineflower-1.11.2.jar";
          sha256 = "1gznakfymhlf589wsgm289w3s0wfr3gysjrc81h4kcvqgxg43qp1";
        };

        nativeBuildInputs = [ pkgs.makeWrapper ];
        dontUnpack = true;

        installPhase = ''
          mkdir -p $out/share/java $out/bin
          cp $src $out/share/java/vineflower.jar

          makeWrapper ${pkgs.jdk21}/bin/java $out/bin/vineflower \
            --add-flags "-jar $out/share/java/vineflower.jar"
        '';
      };
    in
    {
      devShells.${system}.default =
        let
          glfw-libs = with pkgs; [
            # OpenGL / Mesa
            libGL
            libglvnd

            # GLFW + X11 dependencies (Xwayland fallback)
            glfw
            libx11
            libxcursor
            libxrandr
            libxinerama
            libxi
            libxxf86vm
            libxext
            libxrender
            libxtst

            # Wayland
            wayland
            libxkbcommon

            # Audio (OpenAL)
            openal
            libpulseaudio
            alsa-lib

            # Misc native deps LWJGL may dlopen
            flite
            udev
          ];
        in
        pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.gradle
            vineflower
          ];

          JAVA_HOME = "${pkgs.jdk21}";

          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath glfw-libs;
        };

      ## nix run -- build JAR into dist/ ##
      apps.${system}.default = {
        type = "app";
        program = toString (pkgs.writeShellScript "build" ''
          set -euo pipefail
          export JAVA_HOME="${pkgs.jdk21}"

          ${pkgs.gradle}/bin/gradle --no-daemon jar

          mkdir -p dist
          find build/libs -maxdepth 1 -name "*.jar" \
            -not -name "*-sources.jar" \
            -exec cp {} dist/ \;

          echo "Built:"
          ls dist/*.jar
        '');
      };
    };
}
