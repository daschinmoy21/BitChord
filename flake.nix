{
  description = "BitChord desktop dev shell (NixOS)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};

      # Native libraries the JVM loads at runtime (Skiko/GL, AWT, JavaFX WebView, ALSA, FFmpeg).
      runtimeLibs = with pkgs; [
        libglvnd
        glib
        gtk3
        pango
        atk
        cairo
        gdk-pixbuf
        libxtst
        libxxf86vm
        libx11
        libxext
        libxrender
        libxi
        libxcursor
        libxrandr
        alsa-lib
        ffmpeg_6.lib
        fontconfig
        freetype
        zlib
        libpulseaudio
      ];
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          jdk21
          cmake
          gcc
          gnumake
          pkg-config
          ffmpeg_6
          xdg-utils
          playerctl
        ];

        JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";

        shellHook = ''
          export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath runtimeLibs}:/run/opengl-driver/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
          export _JAVA_AWT_WM_NONREPARENTING=1
          echo "BitChord dev shell: run with  ./gradlew :desktopApp:run"
        '';
      };
    };
}
