//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19 --add-modules=jdk.incubator.concurrent
//JAVA_OPTIONS  --enable-preview --add-modules=jdk.incubator.concurrent
//SOURCES **.java

class main {
    public static void main(String[] args) throws InterruptedException {
	System.out.println("single");
	GitHubCrawl.main(new String[] {"single", "/Users/max/code"});

	
    }
}