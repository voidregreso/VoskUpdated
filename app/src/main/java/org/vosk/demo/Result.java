package org.vosk.demo;

import java.util.List;

public class Result {

    private String partial;
    private List<Words> result;
    private String text;

    public String getPartial() {
        return partial;
    }

    public void setPartial(String partial) {
        this.partial = partial;
    }

    public List<Words> getResult() {
        return result;
    }

    public void setResult(List<Words> result) {
        this.result = result;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
