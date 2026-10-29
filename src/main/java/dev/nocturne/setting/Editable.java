package dev.nocturne.setting;

/** Anything the ClickGUI can type into. */
public interface Editable {
    String text();
    void setText(String s);
    /** returns the (possibly transformed) char to accept, or 0 to reject it */
    char filter(char c);
    int maxLen();
}
