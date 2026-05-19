package MVC.Models;

import MVC.Interfaces.Subject;

public class LLMModel implements Subject {
    private String modelName;

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public void addObserver() {

    }

    @Override
    public void removeObserver() {

    }

    @Override
    public void notifyObservers() {

    }
}
