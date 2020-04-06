package org.edu.mail.usage;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * 邮件基本异常类
 */
public class UMailException extends Exception{
    private static final long serialVersionUID = 1L;

    private Throwable nestedException;

    public UMailException() {
        super("Error occurred in UMail application.");
    }

    public UMailException(String message) {
        super(message);
    }

    public UMailException(String message, Throwable nestedException) {
        super(message);
        this.nestedException = nestedException;
    }

    public UMailException(Throwable nestedException) {
        super(nestedException.getMessage());
        this.nestedException = nestedException;
    }
    public Throwable getNestedException() {
        return this.nestedException;
    }

    public String getMessage() {
        return this.nestedException != null ? super.getMessage() + " Nested exception: " + this.nestedException.getMessage() : super.getMessage();
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (this.nestedException != null) {
            System.err.print("Nested exception: ");
            this.nestedException.printStackTrace();
        }
    }

    public void printStackTrace(PrintStream out) {
        super.printStackTrace(out);
        if (this.nestedException != null) {
            out.println("Nested exception: ");
            this.nestedException.printStackTrace(out);
        }
    }

    public void printStackTrace(PrintWriter writer) {
        super.printStackTrace(writer);
        if (this.nestedException != null) {
            writer.println("Nested exception: ");
            this.nestedException.printStackTrace(writer);
        }
    }
}
