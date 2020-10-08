{ pkgs ? import <nixpkgs> {} }:
let 
  pname = "zookeeper";
  version = "3.6.1";
in
pkgs.mkShell {

  src = pkgs.fetchurl {
    url = "mirror://apache/zookeeper/${pname}-${version}/apache-${pname}-${version}-bin.tar.gz";
    sha512 = "1c5cb4d9886fae41bf244a446dd874b73c0fff7a5fc2dda4305041964420cde21e59b388dfd2551464a46bb6918d9d3c3c01ebe68fdbe782074ee360aa830c7d";
  };

  buildInputs = [ pkgs.makeWrapper pkgs.jre ];

  phases = ["unpackPhase" "installPhase"];

  installPhase = ''
    mkdir -p $out
    cp -R conf docs lib $out
    mkdir -p $out/bin
    cp -R bin/{zkCli,zkCleanup,zkEnv,zkServer}.sh $out/bin
    mv $out/conf/zoo_sample.cfg $out/conf/zoo.cfg
    patchShebangs $out/bin
    substituteInPlace $out/bin/zkServer.sh \
        --replace /bin/echo ${pkgs.coreutils}/bin/echo
    for i in $out/bin/{zkCli,zkCleanup,zkServer}.sh; do
      wrapProgram $i \
        --set JAVA_HOME "${pkgs.jre}" \
        --prefix PATH : "${pkgs.bash}/bin"
    done

    echo 'zookeeper.root.logger=CONSOLE' >> $out/conf/log4j.properties
    echo 'zookeeper.auditlog.logger=CONSOLE' >> $out/conf/log4j.properties
    echo 'audit.logger=CONSOLE' >> $out/conf/log4j.properties

    echo 'admin.enableServer=false' >> $out/conf/zoo.cfg
  '';

  shellHook = ''
    alias zkstart='$out/bin/zkServer.sh start-foreground'
    alias zkcli='$out/bin/zkCli.sh'
    alias zkclean='rm -rf /tmp/zookeeper'
  '';
}