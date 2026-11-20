package threatlens.ui;

import javafx.scene.Node;

interface Page {
    Node view();
    default void refresh() {}
}
