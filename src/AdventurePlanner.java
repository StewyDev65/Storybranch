import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.effect.DropShadow;
import java.io.*;
import java.util.*;

public class AdventurePlanner extends Application {

    private Map<String, StoryNode> nodes = new HashMap<>();
    private Pane canvas;
    private ScrollPane scrollPane;
    private DoubleProperty scale = new SimpleDoubleProperty(1.0);
    private StoryNode selectedNode = null;
    private StoryNode startNode = null;
    private double dragStartX, dragStartY;
    private VBox rightPanel;
    private Label statusLabel;
    private TextField titleField;
    private TextArea descriptionArea;
    private String currentFileName = "Untitled Adventure";
    // Map to track node UI elements by node ID
    private Map<String, VBox> nodeBoxes = new HashMap<>();
    private Group canvasContainer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Main layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Create a menu bar
        MenuBar menuBar = createMenuBar(primaryStage);
        root.setTop(menuBar);

        // Create canvas for the story nodes
        canvas = new Pane();
        canvas.getStyleClass().add("canvas");
        canvas.setPrefSize(3000, 2000); // Large initial size
        canvas.setStyle("-fx-background-color: #2C3E50;");

        canvasContainer = new Group(canvas);

        // Create grid pattern programmatically instead of using CSS background-image
        createGridPattern();

        // Add keyboard shortcuts
        setupKeyboardShortcuts(primaryStage);

        // Make canvas scalable and scrollable
        scrollPane = new ScrollPane(canvasContainer);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // Add zoom functionality
        scrollPane.addEventFilter(ScrollEvent.ANY, event -> {
            if (event.isControlDown()) {
                double scaleFactor = (event.getDeltaY() > 0) ? 1.05 : 0.95;
                scale.set(scale.get() * scaleFactor);
                canvas.setScaleX(scale.get());
                canvas.setScaleY(scale.get());
                double mouseX = event.getX();
                double mouseY = event.getY();
                double newMouseX = mouseX * scaleFactor;
                double newMouseY = mouseY * scaleFactor;
                scrollPane.setHvalue(scrollPane.getHvalue() + (mouseX - newMouseX) /
                        (canvasContainer.getBoundsInLocal().getWidth() * scale.get()));
                scrollPane.setVvalue(scrollPane.getVvalue() + (mouseY - newMouseY) /
                        (canvasContainer.getBoundsInLocal().getHeight() * scale.get()));

                // Update after zooming
                updateAfterZoom();

                event.consume();
            }
        });

        // Create right panel for editing
        rightPanel = createRightPanel();
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.setDisable(true);  // Disable until a node is selected

        // Add CSS for decision tags
        addDecisionTagStyles();

        // Add status bar
        statusLabel = new Label("Adventure Game Planner - " + currentFileName);
        statusLabel.getStyleClass().add("status-label");
        HBox statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5));

        // Set up layout
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(scrollPane, rightPanel);
        splitPane.setDividerPositions(0.75);
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        // Initial story node
        createInitialNode();

        // Set up scene
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        primaryStage.setTitle("Adventure Game Planner");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupKeyboardShortcuts(Stage primaryStage) {
        // Add keyboard shortcuts for common actions
        Scene scene = primaryStage.getScene();
        if (scene != null) {
            scene.setOnKeyPressed(event -> {
                if (event.isControlDown()) {
                    switch (event.getCode()) {
                        case S:
                            // Ctrl+S to save
                            saveAdventure(primaryStage, false);
                            event.consume();
                            break;
                        case O:
                            // Ctrl+O to open
                            openAdventure(primaryStage);
                            event.consume();
                            break;
                        case N:
                            // Ctrl+N for new
                            newAdventure();
                            event.consume();
                            break;
                    }
                }
            });
        } else {
            // If scene is not yet created, add the handler after scene creation
            primaryStage.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setOnKeyPressed(event -> {
                        if (event.isControlDown()) {
                            switch (event.getCode()) {
                                case S:
                                    // Ctrl+S to save
                                    saveAdventure(primaryStage, false);
                                    event.consume();
                                    break;
                                case O:
                                    // Ctrl+O to open
                                    openAdventure(primaryStage);
                                    event.consume();
                                    break;
                                case N:
                                    // Ctrl+N for new
                                    newAdventure();
                                    event.consume();
                                    break;
                            }
                        }
                    });
                }
            });
        }
    }

    private MenuBar createMenuBar(Stage primaryStage) {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New");
        MenuItem openItem = new MenuItem("Open");
        MenuItem saveItem = new MenuItem("Save");
        MenuItem saveAsItem = new MenuItem("Save As");
        MenuItem exitItem = new MenuItem("Exit");

        newItem.setOnAction(e -> newAdventure());
        openItem.setOnAction(e -> openAdventure(primaryStage));
        saveItem.setOnAction(e -> saveAdventure(primaryStage, false));
        saveAsItem.setOnAction(e -> saveAdventure(primaryStage, true));
        exitItem.setOnAction(e -> primaryStage.close());

        fileMenu.getItems().addAll(newItem, openItem, saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);

        // Story menu
        Menu storyMenu = new Menu("Story");
        MenuItem addNodeItem = new MenuItem("Add Branch");
        MenuItem deleteNodeItem = new MenuItem("Delete Selected Branch");

        addNodeItem.setOnAction(e -> {
            if (selectedNode != null) {
                addChildNode(selectedNode);
            }
        });

        deleteNodeItem.setOnAction(e -> {
            if (selectedNode != null && !selectedNode.equals(startNode)) {
                deleteNode(selectedNode);
            }
        });

        storyMenu.getItems().addAll(addNodeItem, deleteNodeItem);

        // View menu
        Menu viewMenu = new Menu("View");
        MenuItem zoomInItem = new MenuItem("Zoom In");
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        MenuItem resetZoomItem = new MenuItem("Reset Zoom");

        zoomInItem.setOnAction(e -> {
            scale.set(scale.get() * 1.1);
            canvas.setScaleX(scale.get());
            canvas.setScaleY(scale.get());
        });

        zoomOutItem.setOnAction(e -> {
            scale.set(scale.get() / 1.1);
            canvas.setScaleX(scale.get());
            canvas.setScaleY(scale.get());
        });

        resetZoomItem.setOnAction(e -> {
            scale.set(1.0);
            canvas.setScaleX(scale.get());
            canvas.setScaleY(scale.get());
        });

        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, resetZoomItem);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");

        aboutItem.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About Adventure Game Planner");
            alert.setHeaderText("Adventure Game Planner");
            alert.setContentText("A visual tool for designing choose-your-own-adventure games.\n\n" +
                    "Use the canvas to add branches to your story and create an interactive narrative.");
            alert.showAndWait();
        });

        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, storyMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private VBox createRightPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setMinWidth(300);
        panel.getStyleClass().add("edit-panel");

        Label titleLabel = new Label("Branch Title");
        titleLabel.getStyleClass().add("panel-label");
        titleField = new TextField();
        titleField.getStyleClass().add("title-field");
        titleField.textProperty().addListener((obs, old, newValue) -> {
            if (selectedNode != null) {
                selectedNode.setTitle(newValue);
                updateCanvas();
            }
        });

        Label descriptionLabel = new Label("Branch Description");
        descriptionLabel.getStyleClass().add("panel-label");
        descriptionArea = new TextArea();
        descriptionArea.getStyleClass().add("description-area");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefHeight(200);
        descriptionArea.textProperty().addListener((obs, old, newValue) -> {
            if (selectedNode != null) {
                selectedNode.setDescription(newValue);
                updateCanvas();
            }
        });

        Button addBranchButton = new Button("Add Branch");
        addBranchButton.getStyleClass().add("action-button");
        addBranchButton.setMaxWidth(Double.MAX_VALUE);
        addBranchButton.setOnAction(e -> {
            if (selectedNode != null) {
                addChildNode(selectedNode);
            }
        });

        Button deleteBranchButton = new Button("Delete Branch");
        deleteBranchButton.getStyleClass().add("delete-button");
        deleteBranchButton.setMaxWidth(Double.MAX_VALUE);
        deleteBranchButton.setOnAction(e -> {
            if (selectedNode != null && !selectedNode.equals(startNode)) {
                deleteNode(selectedNode);
            }
        });

        // Add styling to buttons
        addBranchButton.setStyle("-fx-background-color: #2E86C1; -fx-text-fill: white;");
        deleteBranchButton.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white;");

        panel.getChildren().addAll(
                titleLabel, titleField,
                descriptionLabel, descriptionArea,
                new Separator(),
                addBranchButton,
                deleteBranchButton
        );

        return panel;
    }

    private void createInitialNode() {
        startNode = new StoryNode("1", "Start Your Adventure", "This is where your story begins...");
        startNode.setPosition(300, 100);
        nodes.put(startNode.getId(), startNode);
        drawNode(startNode);
    }

    private void addChildNode(StoryNode parent) {
        String newId = UUID.randomUUID().toString().substring(0, 8);
        StoryNode child = new StoryNode(newId, "New Branch", "Describe what happens in this branch...");

        // Position child based on parent's position and existing children
        int childCount = parent.getChildren().size();
        double x = parent.getX() + (childCount * 200) - (parent.getChildren().size() * 100);
        double y = parent.getY() + 200;
        child.setPosition(x, y);

        // Add connections
        parent.addChild(child);
        nodes.put(child.getId(), child);

        // Draw the node and connections
        drawNode(child);
        drawConnections();

        // Set as selected
        selectNode(child);
    }

    private void deleteNode(StoryNode node) {
        if (node.equals(startNode)) {
            return; // Don't delete start node
        }

        // Remove from parent's children
        for (StoryNode potentialParent : nodes.values()) {
            potentialParent.removeChild(node);
        }

        // Delete this node and all its descendants
        List<StoryNode> nodesToDelete = new ArrayList<>();
        collectDescendants(node, nodesToDelete);

        for (StoryNode deleteNode : nodesToDelete) {
            // Remove the nodeBox from the canvas
            VBox nodeBox = nodeBoxes.remove(deleteNode.getId());
            if (nodeBox != null) {
                canvas.getChildren().remove(nodeBox);
            }
            nodes.remove(deleteNode.getId());
        }

        // Clear selection and update canvas
        selectedNode = null;
        rightPanel.setDisable(true);
        drawConnections(); // Update connections after removing nodes
    }

    private void collectDescendants(StoryNode node, List<StoryNode> descendants) {
        descendants.add(node);
        for (StoryNode child : new ArrayList<>(node.getChildren())) {
            collectDescendants(child, descendants);
        }
    }

    private void selectNode(StoryNode node) {
        // Clear previous selection visual if there was one
        if (selectedNode != null && nodeBoxes.containsKey(selectedNode.getId())) {
            VBox prevSelectedBox = nodeBoxes.get(selectedNode.getId());
            prevSelectedBox.getStyleClass().remove("selected-node");
        }

        selectedNode = node;
        titleField.setText(node.getTitle());
        descriptionArea.setText(node.getDescription());
        rightPanel.setDisable(false);

        // Update visual selection on canvas
        if (nodeBoxes.containsKey(node.getId())) {
            VBox selectedBox = nodeBoxes.get(node.getId());
            selectedBox.getStyleClass().add("selected-node");
        }
    }

    private void updateCanvas() {
        // Clear the canvas and node tracking
        canvas.getChildren().clear();
        nodeBoxes.clear();

        // Recreate grid
        createGridPattern();

        // Draw all nodes and connections
        drawAllNodes();
        drawConnections();
        adjustCanvasSize();
    }

    private void drawAllNodes() {
        for (StoryNode node : nodes.values()) {
            drawNode(node);
        }
    }

    private void drawNode(StoryNode node) {
        VBox nodeBox = new VBox(5);
        nodeBox.setLayoutX(node.getX());
        nodeBox.setLayoutY(node.getY());
        nodeBox.setPrefWidth(180);
        nodeBox.setMinHeight(100); // Ensure minimum height to accommodate description
        nodeBox.setPadding(new Insets(10));
        nodeBox.setAlignment(Pos.CENTER);
        nodeBox.getStyleClass().add("story-node");

        // Set special style for the selected node
        if (node.equals(selectedNode)) {
            nodeBox.getStyleClass().add("selected-node");
        }

        // Special style for start node
        if (node.equals(startNode)) {
            nodeBox.getStyleClass().add("start-node");
        }

        // Add drop shadow effect
        DropShadow dropShadow = new DropShadow();
        dropShadow.setColor(Color.rgb(0, 0, 0, 0.4));
        dropShadow.setOffsetX(3);
        dropShadow.setOffsetY(3);
        dropShadow.setRadius(5);
        nodeBox.setEffect(dropShadow);

        Text titleText = new Text(node.getTitle());
        titleText.getStyleClass().add("node-title");

        // Add a shortened description that cuts off after 2 lines
        String shortenedDescription = node.getDescription();
        if (shortenedDescription != null && !shortenedDescription.isEmpty()) {
            // Truncate to roughly 2 lines (approximately 80 characters)
            if (shortenedDescription.length() > 80) {
                shortenedDescription = shortenedDescription.substring(0, 77) + "...";
            }

            Text descriptionText = new Text(shortenedDescription);
            descriptionText.getStyleClass().add("node-description");
            descriptionText.setWrappingWidth(160); // Ensure text wraps within node

            nodeBox.getChildren().addAll(titleText, descriptionText);
        } else {
            nodeBox.getChildren().add(titleText);
        }

        Label idLabel = new Label("ID: " + node.getId());
        idLabel.getStyleClass().add("node-id");

        nodeBox.getChildren().add(idLabel);

        // Make node draggable with continuous updates
        nodeBox.setOnMousePressed(e -> {
            selectNode(node);
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
        });

        nodeBox.setOnMouseDragged(e -> {
            double offsetX = (e.getSceneX() - dragStartX) / scale.get();
            double offsetY = (e.getSceneY() - dragStartY) / scale.get();
            node.setPosition(node.getX() + offsetX, node.getY() + offsetY);
            nodeBox.setLayoutX(node.getX());
            nodeBox.setLayoutY(node.getY());
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();

            // Update connections in real-time during drag
            drawConnections();
        });

        // Add to tracking map and canvas
        nodeBoxes.put(node.getId(), nodeBox);
        canvas.getChildren().add(nodeBox);

        // Double click to edit
        nodeBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectNode(node);
            }
        });
    }

    private void drawConnections() {
        // Clear existing connections and decision labels
        canvas.getChildren().removeIf(node ->
                node instanceof Line ||
                        (node instanceof HBox && node.getStyleClass().contains("decision-tag")));

        // Draw connections for each node
        for (StoryNode node : nodes.values()) {
            for (StoryNode child : node.getChildren()) {
                drawConnection(node, child);
            }
        }
    }

    private void drawConnection(StoryNode parent, StoryNode child) {
        // Calculate center points
        double startX = parent.getX() + 90;
        double startY = parent.getY() + 40;
        double endX = child.getX() + 90;
        double endY = child.getY();

        Line line = new Line(startX, startY, endX, endY);
        line.getStyleClass().add("connection-line");
        line.setStrokeWidth(2);

        // Add line behind nodes (to the beginning of the children list)
        canvas.getChildren().add(0, line);

        // Add decision tag at the midpoint of the line
        addDecisionTag(parent, child, startX, startY, endX, endY);
    }

    private void addDecisionTag(StoryNode parent, StoryNode child, double startX, double startY, double endX, double endY) {
        // Calculate midpoint of the line
        double midX = (startX + endX) / 2;
        double midY = (startY + endY) / 2;

        // Get decision text
        String decisionText = parent.getDecisionText(child.getId());

        // Create a label for the decision text
        Label label = new Label(decisionText);
        label.getStyleClass().add("decision-text");

        // Create edit button
        Button editButton = new Button("✎");
        editButton.getStyleClass().add("edit-decision-button");
        editButton.setVisible(false); // Only show on hover

        // Create container for the decision tag
        HBox decisionTag = new HBox(5, label, editButton);
        decisionTag.getStyleClass().add("decision-tag");
        decisionTag.setAlignment(Pos.CENTER);

        // Position the tag
        decisionTag.setLayoutX(midX - (decisionTag.prefWidth(-1) / 2));
        decisionTag.setLayoutY(midY - 15);

        // Add hover effect to show edit button
        decisionTag.setOnMouseEntered(e -> editButton.setVisible(true));
        decisionTag.setOnMouseExited(e -> editButton.setVisible(false));

        // Handle edit button click
        editButton.setOnAction(e -> {
            // Create a text input dialog
            TextInputDialog dialog = new TextInputDialog(decisionText);
            dialog.setTitle("Edit Decision");
            dialog.setHeaderText("Enter decision text");
            dialog.setContentText("This text will appear as a choice in your game:");

            // Show the dialog and get the result
            dialog.showAndWait().ifPresent(result -> {
                parent.setDecisionText(child.getId(), result);
                label.setText(result);

                // Adjust position after text change
                decisionTag.setLayoutX(midX - (decisionTag.getWidth() / 2));
            });
        });

        // Also make the label itself clickable to edit
        label.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editButton.fire(); // Simulate clicking the edit button
            }
        });

        // Add the decision tag to the canvas
        canvas.getChildren().add(decisionTag);
    }

    private void adjustCanvasSize() {
        double maxX = 1000;
        double maxY = 600;

        for (StoryNode node : nodes.values()) {
            maxX = Math.max(maxX, node.getX() + 250);
            maxY = Math.max(maxY, node.getY() + 250);
        }

        // Add extra padding to ensure nodes don't get too close to the edge
        maxX += 200;
        maxY += 200;

        // Only resize if we need more space
        if (maxX > canvas.getPrefWidth() || maxY > canvas.getPrefHeight()) {
            canvas.setPrefSize(maxX, maxY);

            // Recreate grid pattern for new size
            createGridPattern();
        }
    }

    private void updateAfterZoom() {
        // This method ensures everything is properly scaled and visible after zooming
        drawConnections();

        // Make sure the background covers the visible area
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();

        double requiredWidth = viewportWidth / scale.get();
        double requiredHeight = viewportHeight / scale.get();

        // Only expand if needed
        double newWidth = Math.max(canvas.getPrefWidth(), requiredWidth);
        double newHeight = Math.max(canvas.getPrefHeight(), requiredHeight);

        if (newWidth > canvas.getPrefWidth() || newHeight > canvas.getPrefHeight()) {
            canvas.setPrefSize(newWidth, newHeight);
            createGridPattern();
        }
    }

    private void createGridPattern() {
        // Clear existing grid dots
        canvas.getChildren().removeIf(node ->
                node instanceof Group && ((Group)node).getStyleClass().contains("grid-group"));

        // Create grid of dots
        int spacing = 30;
        int dotSize = 1;
        Color dotColor = Color.rgb(58, 80, 107); // #3A506B

        // Calculate how many dots we need based on the canvas size
        int dotsX = (int) Math.ceil(canvas.getPrefWidth() / spacing) + 1;
        int dotsY = (int) Math.ceil(canvas.getPrefHeight() / spacing) + 1;

        Group gridGroup = new Group();
        gridGroup.getStyleClass().add("grid-group");

        for (int i = 0; i < dotsX; i++) {
            for (int j = 0; j < dotsY; j++) {
                int x = i * spacing;
                int y = j * spacing;
                javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(x, y, dotSize, dotColor);
                dot.getStyleClass().add("grid-dot");
                gridGroup.getChildren().add(dot);
            }
        }

        // Add the entire grid group to the canvas at the beginning
        canvas.getChildren().add(0, gridGroup);
    }

    private void addDecisionTagStyles() {
        // We'll use the external CSS file instead of programmatic styles
        // No implementation needed here since styles are defined in style.css
    }

    private void newAdventure() {
        nodeBoxes.clear();
        nodes.clear();
        selectedNode = null;
        rightPanel.setDisable(true);
        currentFileName = "Untitled Adventure";
        statusLabel.setText("Adventure Game Planner - " + currentFileName);
        createInitialNode();
    }

    private void openAdventure(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Adventure File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Adventure Files (*.adv)", "*.adv"));
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                // Clear existing nodes
                nodes.clear();
                nodeBoxes.clear();

                // Read nodes map
                Map<String, StoryNode> loadedNodes = (Map<String, StoryNode>) in.readObject();
                nodes.putAll(loadedNodes);

                // Read start node ID
                String startNodeId = (String) in.readObject();
                startNode = nodes.get(startNodeId);

                currentFileName = file.getAbsolutePath();
                statusLabel.setText("Adventure Game Planner - " + file.getName());

                // Update UI
                selectedNode = null;
                rightPanel.setDisable(true);
                updateCanvas();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Opening File");
                alert.setHeaderText("Could not open the adventure file.");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void saveAdventure(Stage stage, boolean saveAs) {
        File file = null;

        if (currentFileName.equals("Untitled Adventure") || saveAs) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Adventure File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Adventure Files (*.adv)", "*.adv"));

            // Set initial file name
            if (currentFileName.equals("Untitled Adventure")) {
                fileChooser.setInitialFileName("MyAdventure.adv");
            } else {
                File currentFile = new File(currentFileName);
                fileChooser.setInitialFileName(currentFile.getName());

                // Set initial directory to the directory of the current file
                if (currentFile.getParentFile() != null) {
                    fileChooser.setInitialDirectory(currentFile.getParentFile());
                }
            }

            file = fileChooser.showSaveDialog(stage);
        } else {
            file = new File(currentFileName);
        }

        if (file != null) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                // Write nodes map
                out.writeObject(nodes);

                // Write start node ID
                out.writeObject(startNode.getId());

                currentFileName = file.getAbsolutePath();
                statusLabel.setText("Adventure Game Planner - " + file.getName());
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Saving File");
                alert.setHeaderText("Could not save the adventure file.");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    // Story Node class
    public static class StoryNode implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String title;
        private String description;
        private double x, y;
        private Map<String, String> childDecisions = new HashMap<>(); // Maps child node ID to decision text
        private List<StoryNode> children = new ArrayList<>();

        public StoryNode(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public void setPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public List<StoryNode> getChildren() {
            return children;
        }

        public void addChild(StoryNode child) {
            if (!children.contains(child)) {
                children.add(child);
                setDecisionText(child.getId(), "Make a choice..."); // Default decision text
            }
        }

        public void removeChild(StoryNode child) {
            children.remove(child);
            childDecisions.remove(child.getId());
        }

        public String getDecisionText(String childId) {
            return childDecisions.getOrDefault(childId, "Make a choice...");
        }

        public void setDecisionText(String childId, String text) {
            childDecisions.put(childId, text);
        }
    }
}