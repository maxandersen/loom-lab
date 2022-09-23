package dev.nipafx.lab.loom;
//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19 --add-modules=jdk.incubator.concurrent
//JAVA_OPTIONS  --enable-preview --add-modules=jdk.incubator.concurrent
//SOURCES **.java

import dev.nipafx.lab.loom.crawl.GitHubCrawl;

class main {
	public static void main(String[] args) throws Exception {
		System.out.println("single");
		GitHubCrawl.main(new String[] { "/Users/max/code", "1" });

	}
}