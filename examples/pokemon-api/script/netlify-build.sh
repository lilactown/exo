wget -q -T 1 -t 1 https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.16.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.16.1_1.tar.gz
# Optional; check integrity of download. Would be safer to download the sha256.txt
# file once, commit it to the repo, then run `sha256sum -c` with that on each build.
wget -O- -q -T 1 -t 1 https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.16.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.16.1_1.tar.gz.sha256.txt | sha256sum -c

tar xzf OpenJDK11U-jdk_x64_linux_hotspot_11.0.16.1_1.tar.gz

# shadow-cljs needs the jdk on the path. The easiest way to ensure that is to put
# this line in the script file that calls shadow-cljs.
export PATH=$PWD/jdk-11.0.16.1+1/bin:$PATH

java -version

npx shadow-cljs releas app
