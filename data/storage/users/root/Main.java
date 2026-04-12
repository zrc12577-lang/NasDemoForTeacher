package abc442.TestB;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        int curV = 0;
        int curS = 0;

        int n = sc.nextInt();
        while (n-- > 0) {
            int m = sc.nextInt();
            if(m == 1) curV++;
            else if(m == 2) {
                if(curV >=1 ) curV--;
            }else if(m == 3) {
                curS++;
            }

            if(curV >= 3 && curS%2 == 1) System.out.println("Yes");
            else System.out.println("No");
        }

    }
}
