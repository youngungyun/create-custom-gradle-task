package dev.lab.app;

public class Main {
	void main() {
		IO.println("앱 버전 : " + BuildInfo.VERSION);
		IO.println("커밋    :" + BuildInfo.GIT_COMMIT);
	}
}