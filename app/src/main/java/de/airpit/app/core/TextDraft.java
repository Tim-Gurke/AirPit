package de.airpit.app.core;

/** Keeps unacknowledged and newly edited text; the previous successful text stays in RAM only. */
public final class TextDraft {
    public String value="",lastSent="";
    public void complete(String source,boolean success){if(success){lastSent=source;if(value.equals(source))value="";}}
    public void restore(){if(value.isEmpty())value=lastSent;}
}
