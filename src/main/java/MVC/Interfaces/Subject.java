package MVC.Interfaces;

public interface Subject {
    public void addObserver();
    public void removeObserver();
    public void notifyObservers();
}
