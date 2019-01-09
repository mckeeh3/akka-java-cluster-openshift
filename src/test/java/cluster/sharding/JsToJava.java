package cluster.sharding;

import java.io.*;

public class JsToJava {
    public static void main(String[] args) {
        File file = new File(JsToJava.class.getClassLoader().getResource("force-collapsible.html").getFile());
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line = br.readLine()) != null) {
                System.out.printf("line(html, \"%s\");%n", line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
