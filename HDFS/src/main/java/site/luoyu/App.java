package site.luoyu;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
//        System.out.println( "Hello World!" );

        A a = new B();
        B b = (B) a;
        b.sayHellow();
    }
    static class A{
        public void sayHellow(){
            System.out.println("B");
        }
    }
    static class B extends A{
        public void sayHellow(){
            System.out.println("B");
        }
    }
}
