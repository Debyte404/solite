package solite.core;

public interface Action {
    void execute() throws MoveFailedException;
    void undo() throws MoveFailedException;
}
