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
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle
          vineflower
        ];

        JAVA_HOME = "${pkgs.jdk21}";
      };
    };
}
