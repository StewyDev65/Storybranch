import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TextInputDialog;
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

    private boolean decisionTagModeActive = false;
    private List<CustomDecisionTag> customDecisionTags = new ArrayList<>();
    private Button toggleDecisionTagButton;

    private CustomConnection selectedConnection = null;

    private ProgressBar savingProgressBar;

    private Stack<UndoableAction> undoStack = new Stack<>();
    private Button toggleConnectionButton; // Make this a class member
    private double oldNodeX, oldNodeY; // For tracking node position for undo

    private StoryNode draggedNode = null;

    private boolean connectionModeActive = false;
    private double connectionStartX, connectionStartY;
    private double connectionTempEndX, connectionTempEndY;
    private List<CustomConnection> customConnections = new ArrayList<>();
    private Line previewLine = null;

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
        canvas.setStyle("-fx-background-color: #0a0b10;");

        canvasContainer = new Group(canvas);

        // Create grid pattern programmatically instead of using CSS background-image
        createGridPattern();

        // Set up the canvas click handler for selection clearing
        setupCanvasClickHandler();

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

        setupStatusBar();

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
                        case Z:
                            // Ctrl+Z to undo
                            undo();
                            event.consume();
                            break;
                    }
                } else {
                    switch (event.getCode()) {
                        case DELETE:
                            // Delete selected connection
                            if (selectedConnection != null) {
                                deleteCustomConnection(selectedConnection);
                                event.consume();
                            }
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
                                case Z:
                                    // Ctrl+Z to undo
                                    undo();
                                    event.consume();
                                    break;
                            }
                        } else {
                            switch (event.getCode()) {
                                case DELETE:
                                    // Delete selected connection
                                    if (selectedConnection != null) {
                                        deleteCustomConnection(selectedConnection);
                                        event.consume();
                                    }
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
        exitItem.setOnAction(e -> {
            primaryStage.close();
            Platform.exit();
            System.exit(0);
        });

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
        addBranchButton.setStyle("-fx-background-color: #3b8272; -fx-text-fill: #f0f0f0;");
        deleteBranchButton.setStyle("-fx-background-color: #723093; -fx-text-fill: #f0f0f0;");

        panel.getChildren().addAll(
                titleLabel, titleField,
                descriptionLabel, descriptionArea,
                new Separator(),
                addBranchButton,
                deleteBranchButton
        );

        toggleConnectionButton = new Button("Enter Free Connection Mode");
        toggleConnectionButton.getStyleClass().add("action-button");
        toggleConnectionButton.setMaxWidth(Double.MAX_VALUE);
        toggleConnectionButton.setStyle("-fx-background-color: #3b91c5; -fx-text-fill: #f0f0f0;"); // Purple styling
        toggleConnectionButton.setOnAction(e -> toggleConnectionMode());

        panel.getChildren().addAll(
                new Separator(),
                toggleConnectionButton
        );

        toggleDecisionTagButton = new Button("Enter Custom Tag Mode");
        toggleDecisionTagButton.getStyleClass().add("action-button");
        toggleDecisionTagButton.setMaxWidth(Double.MAX_VALUE);
        toggleDecisionTagButton.setStyle("-fx-background-color: #5e4cc3; -fx-text-fill: #f0f0f0;"); // Green styling
        toggleDecisionTagButton.setOnAction(e -> toggleDecisionTagMode());

        panel.getChildren().addAll(
                new Separator(),
                toggleDecisionTagButton
        );

        return panel;
    }

    private void toggleDecisionTagMode() {
        // Exit connection mode if it's active
        if (connectionModeActive) {
            toggleConnectionMode();
        }

        decisionTagModeActive = !decisionTagModeActive;

        if (decisionTagModeActive) {
            // Visual indication that tag mode is active
            toggleDecisionTagButton.setText("Exit Tag Mode");
            toggleDecisionTagButton.setStyle("-fx-background-color: #a1412e; -fx-text-fill: #f0f0f0;"); // Red when active
            statusLabel.setText("TAG MODE ACTIVE - Click anywhere to place custom tags");
            // Add mouse handler for canvas for tags
            setupDecisionTagHandler();
        } else {
            // Reset the button when inactive
            toggleDecisionTagButton.setText("Enter Custom Tag Mode");
            toggleDecisionTagButton.setStyle("-fx-background-color: #5e4cc3; -fx-text-fill: #f0f0f0;"); // Back to green
            statusLabel.setText("Adventure Game Planner - " + currentFileName);
            // Remove handler
            clearDecisionTagHandler();
        }
    }

    private void setupDecisionTagHandler() {
        canvas.setOnMouseClicked(e -> {
            if (decisionTagModeActive) {
                double clickX = e.getX();
                double clickY = e.getY();

                // Create a TextInputDialog to get the tag text
                TextInputDialog dialog = new TextInputDialog("Tag text...");
                dialog.setTitle("Create Custom Tag");
                dialog.setHeaderText("Enter tag text");
                dialog.setContentText("Text for this tag:");

                dialog.showAndWait().ifPresent(text -> {
                    // Create a new custom decision tag
                    CustomDecisionTag tag = new CustomDecisionTag(clickX, clickY, text);
                    customDecisionTags.add(tag);

                    // Add to undo stack
                    undoStack.push(new AddDecisionTagAction(tag));

                    // Draw the tag
                    drawCustomDecisionTag(tag);
                });

                e.consume();
            }
        });
    }

    private void clearDecisionTagHandler() {
        // Reset canvas click handler to default
        setupCanvasClickHandler();
    }

    private void drawCustomDecisionTag(CustomDecisionTag tag) {
        // Create label for the tag text
        Label label = new Label(tag.getText());
        label.getStyleClass().add("decision-text");

        // Create edit button
        Button editButton = new Button("✎");
        editButton.getStyleClass().add("edit-decision-button");
        editButton.setVisible(false); // Only show on hover

        // Create delete button
        Button deleteButton = new Button("×");
        deleteButton.getStyleClass().add("edit-decision-button");
        deleteButton.setVisible(false); // Only show on hover

        // Create container for the tag
        HBox tagBox = new HBox(5, label, editButton, deleteButton);
        tagBox.getStyleClass().addAll("decision-tag", "custom-decision-tag"); // Add distinct class
        tagBox.setAlignment(Pos.CENTER);

        // Position the tag
        tagBox.setLayoutX(tag.getX());
        tagBox.setLayoutY(tag.getY());

        // Add hover effect to show buttons
        tagBox.setOnMouseEntered(e -> {
            editButton.setVisible(true);
            deleteButton.setVisible(true);
        });
        tagBox.setOnMouseExited(e -> {
            editButton.setVisible(false);
            deleteButton.setVisible(false);
        });

        // Make draggable
        tagBox.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();

            // For undo support
            oldNodeX = tag.getX();
            oldNodeY = tag.getY();

            e.consume();
        });

        tagBox.setOnMouseDragged(e -> {
            // Calculate offset considering scale
            double offsetX = (e.getSceneX() - dragStartX) / scale.get();
            double offsetY = (e.getSceneY() - dragStartY) / scale.get();

            // Update position
            double newX = tag.getX() + offsetX;
            double newY = tag.getY() + offsetY;

            tagBox.setLayoutX(newX);
            tagBox.setLayoutY(newY);

            // Update model - CORRECTED THIS LINE
            tag.x = newX;
            tag.y = newY;

            // Update for next drag event
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();

            e.consume();
        });

        tagBox.setOnMouseReleased(e -> {
            // Record last position for undo
            if (oldNodeX != tagBox.getLayoutX() || oldNodeY != tagBox.getLayoutY()) {
                // Update tag position in model
                tag.x = tagBox.getLayoutX();
                tag.y = tagBox.getLayoutY();

                undoStack.push(new MoveDecisionTagAction(tag, oldNodeX, oldNodeY));
            }
            e.consume();
        });

        // Handle edit button click
        editButton.setOnAction(e -> {
            // Create a text input dialog
            TextInputDialog dialog = new TextInputDialog(tag.getText());
            dialog.setTitle("Edit Tag");
            dialog.setHeaderText("Edit tag text");
            dialog.setContentText("New text:");

            // Store old text for undo
            String oldText = tag.getText();

            // Show the dialog and get the result
            dialog.showAndWait().ifPresent(result -> {
                tag.setText(result);
                label.setText(result);

                // Add to undo stack
                undoStack.push(new EditDecisionTagAction(tag, oldText));
            });
        });

        // Handle delete button click
        deleteButton.setOnAction(e -> {
            deleteCustomDecisionTag(tag);
        });

        // Also make the label itself clickable to edit
        label.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editButton.fire(); // Simulate clicking the edit button
            }
        });

        // Store reference to UI element
        tag.setTagBox(tagBox);

        // Add the tag to the canvas
        canvas.getChildren().add(tagBox);
    }

    private void deleteCustomDecisionTag(CustomDecisionTag tag) {
        // Remove from list
        customDecisionTags.remove(tag);

        // Remove visual element
        if (tag.getTagBox() != null) {
            canvas.getChildren().remove(tag.getTagBox());
        }

        // Add to undo stack
        undoStack.push(new DeleteDecisionTagAction(tag));
    }

    private void toggleConnectionMode() {
        connectionModeActive = !connectionModeActive;

        if (connectionModeActive) {
            // Visual indication that connection mode is active
            toggleConnectionButton.setText("Exit Connection Mode");
            toggleConnectionButton.setStyle("-fx-background-color: #a1412e; -fx-text-fill: #f0f0f0;"); // Red when active
            statusLabel.setText("CONNECTION MODE ACTIVE - Click and drag to create connections");
            // Add mouse handlers for canvas for connections
            setupConnectionHandlers();
        } else {
            // Reset the button when inactive
            toggleConnectionButton.setText("Enter Free Connection Mode");
            toggleConnectionButton.setStyle("-fx-background-color: #3b91c5; -fx-text-fill: #f0f0f0;"); // Back to purple
            statusLabel.setText("Adventure Game Planner - " + currentFileName);
            // Remove temp handlers
            clearConnectionHandlers();
        }
    }

    private void setupConnectionHandlers() {
        canvas.setOnMousePressed(e -> {
            if (connectionModeActive) {
                connectionStartX = e.getX();
                connectionStartY = e.getY();

                // Create preview line
                previewLine = new Line(connectionStartX, connectionStartY, connectionStartX, connectionStartY);
                previewLine.getStyleClass().add("custom-connection-line");
                previewLine.setStroke(javafx.scene.paint.Color.rgb(107, 76, 140)); // Purple color
                previewLine.setStrokeWidth(2);
                previewLine.getStrokeDashArray().addAll(10d, 5d); // Dashed line
                canvas.getChildren().add(previewLine);
                e.consume();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (connectionModeActive && previewLine != null) {
                connectionTempEndX = e.getX();
                connectionTempEndY = e.getY();
                previewLine.setEndX(connectionTempEndX);
                previewLine.setEndY(connectionTempEndY);
                e.consume();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (connectionModeActive && previewLine != null) {
                canvas.getChildren().remove(previewLine);

                // Create permanent connection
                CustomConnection connection = new CustomConnection(
                        connectionStartX, connectionStartY,
                        connectionTempEndX, connectionTempEndY
                );
                customConnections.add(connection);

                // Add to undo stack
                undoStack.push(new AddConnectionAction(connection));

                // Draw the permanent connection with arrow head
                drawCustomConnection(connection);

                previewLine = null;
                e.consume();
            }
        });
    }

    private void clearConnectionHandlers() {
        canvas.setOnMousePressed(null);
        canvas.setOnMouseDragged(null);
        canvas.setOnMouseReleased(null);
    }

    private void drawCustomConnection(CustomConnection connection) {
        // Create the visible line with original styling
        Line visibleLine = new Line(
                connection.getStartX(), connection.getStartY(),
                connection.getEndX(), connection.getEndY());

        // Style the visible line
        visibleLine.getStyleClass().add("custom-connection-line");
        visibleLine.setStroke(javafx.scene.paint.Color.rgb(107, 76, 140)); // Purple color
        visibleLine.setStrokeWidth(2);
        visibleLine.getStrokeDashArray().addAll(10d, 5d); // Dashed line

        // Create an invisible, thicker line for easier selection
        Line selectionLine = new Line(
                connection.getStartX(), connection.getStartY(),
                connection.getEndX(), connection.getEndY());
        selectionLine.setStroke(javafx.scene.paint.Color.TRANSPARENT);
        selectionLine.setStrokeWidth(15); // Much wider for easier selection
        selectionLine.setCursor(javafx.scene.Cursor.HAND); // Change cursor on hover

        // Make the selection line handle clicks
        selectionLine.setOnMouseClicked(e -> {
            // Clear previous selection
            clearConnectionSelection();

            // Select this connection
            selectedConnection = connection;
            visibleLine.getStyleClass().add("selected-connection-line");

            // Also select the arrow head
            if (connection.getArrowHead() != null) {
                connection.getArrowHead().getStyleClass().add("selected-connection-arrow");
            }

            // Show a delete button near the line
            showConnectionDeleteButton(connection, visibleLine);

            e.consume();
        });

        // Calculate the angle of the line
        double arrowLength = 15;
        double dx = connection.getEndX() - connection.getStartX();
        double dy = connection.getEndY() - connection.getStartY();
        double angle = Math.atan2(dy, dx);

        // Create arrow head
        javafx.scene.shape.Polygon arrowHead = new javafx.scene.shape.Polygon();
        arrowHead.getPoints().addAll(
                connection.getEndX(), connection.getEndY(),
                connection.getEndX() - arrowLength * Math.cos(angle - Math.PI/6),
                connection.getEndY() - arrowLength * Math.sin(angle - Math.PI/6),
                connection.getEndX() - arrowLength * Math.cos(angle + Math.PI/6),
                connection.getEndY() - arrowLength * Math.sin(angle + Math.PI/6)
        );
        arrowHead.setFill(javafx.scene.paint.Color.rgb(107, 76, 140)); // Purple color
        arrowHead.getStyleClass().add("custom-connection-arrow");
        arrowHead.setCursor(javafx.scene.Cursor.HAND); // Change cursor on hover

        // Make the arrow head also selectable
        arrowHead.setOnMouseClicked(e -> {
            // Clear previous selection
            clearConnectionSelection();

            // Select this connection
            selectedConnection = connection;
            visibleLine.getStyleClass().add("selected-connection-line");
            arrowHead.getStyleClass().add("selected-connection-arrow");

            // Show a delete button near the line
            showConnectionDeleteButton(connection, visibleLine);

            e.consume();
        });

        // Store references to the visual elements in the connection object
        connection.setLine(visibleLine);
        connection.setArrowHead(arrowHead);
        connection.setSelectionLine(selectionLine); // Store the selection line too

        // Add all elements to the canvas in the right order
        canvas.getChildren().add(0, visibleLine); // Visible line at the bottom
        canvas.getChildren().add(arrowHead);
        canvas.getChildren().add(selectionLine); // Invisible selection line on top
    }

    private void clearConnectionSelection() {
        // Remove selected-connection-line class from ALL lines
        for (Node node : canvas.getChildren()) {
            if (node instanceof Line) {
                node.getStyleClass().remove("selected-connection-line");
            } else if (node instanceof javafx.scene.shape.Polygon) {
                node.getStyleClass().remove("selected-connection-arrow");
            }
        }

        // Remove any delete buttons
        canvas.getChildren().removeIf(node ->
                node instanceof Button && node.getStyleClass().contains("connection-delete-button"));

        selectedConnection = null;
    }

    private void showConnectionDeleteButton(CustomConnection connection, Line line) {
        // Create a delete button
        Button deleteButton = new Button("X");
        deleteButton.getStyleClass().add("connection-delete-button");

        // Position the button near the middle of the line
        double midX = (connection.getStartX() + connection.getEndX()) / 2;
        double midY = (connection.getStartY() + connection.getEndY()) / 2;

        deleteButton.setLayoutX(midX - 10);
        deleteButton.setLayoutY(midY - 10);

        // Set the action to delete the connection
        deleteButton.setOnAction(e -> {
            deleteCustomConnection(connection);
        });

        canvas.getChildren().add(deleteButton);
    }

    private void deleteCustomConnection(CustomConnection connection) {
        // Remove the connection from our list
        customConnections.remove(connection);

        // Remove the visual elements from the canvas
        if (connection.getLine() != null) {
            canvas.getChildren().remove(connection.getLine());
        }

        if (connection.getSelectionLine() != null) {
            canvas.getChildren().remove(connection.getSelectionLine());
        }

        if (connection.getArrowHead() != null) {
            canvas.getChildren().remove(connection.getArrowHead());
        }

        // Remove any delete buttons
        canvas.getChildren().removeIf(node ->
                node instanceof Button && node.getStyleClass().contains("connection-delete-button"));

        // Add to undo stack
        undoStack.push(new DeleteConnectionAction(connection));

        // Clear the selection
        selectedConnection = null;

        // Force a redraw to ensure everything is properly updated
        updateCanvas();
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

        // Clear previous selection class from the parent node UI
        if (selectedNode != null && nodeBoxes.containsKey(selectedNode.getId())) {
            VBox prevSelectedBox = nodeBoxes.get(selectedNode.getId());
            prevSelectedBox.getStyleClass().remove("selected-node");
        }

        // Set child as selected
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
        // Store existing custom connection references
        CustomConnection tempSelectedConnection = selectedConnection;

        // Remove all visual elements for custom connections
        for (CustomConnection connection : customConnections) {
            if (connection.getLine() != null) {
                canvas.getChildren().remove(connection.getLine());
                connection.setLine(null);
            }
            if (connection.getSelectionLine() != null) {
                canvas.getChildren().remove(connection.getSelectionLine());
                connection.setSelectionLine(null);
            }
            if (connection.getArrowHead() != null) {
                canvas.getChildren().remove(connection.getArrowHead());
                connection.setArrowHead(null);
            }
        }

        for (CustomDecisionTag tag : customDecisionTags) {
            if (tag.getTagBox() != null) {
                canvas.getChildren().remove(tag.getTagBox());
                tag.setTagBox(null);
            }
        }

        // Remove delete buttons
        canvas.getChildren().removeIf(node ->
                node instanceof Button && node.getStyleClass().contains("connection-delete-button"));

        // Clear the canvas and node tracking
        canvas.getChildren().clear();
        nodeBoxes.clear();

        // Recreate grid
        createGridPattern();

        // Draw all nodes and connections
        drawAllNodes();
        drawConnections();

        // Redraw custom connections
        for (CustomConnection connection : customConnections) {
            drawCustomConnection(connection);
        }

        for (CustomDecisionTag tag : customDecisionTags) {
            drawCustomDecisionTag(tag);
        }

        adjustCanvasSize();

        // Restore visual selection if needed
        if (tempSelectedConnection != null && customConnections.contains(tempSelectedConnection)) {
            selectedConnection = tempSelectedConnection;
            Line line = selectedConnection.getLine();
            if (line != null) {
                line.getStyleClass().add("selected-connection-line");

                if (selectedConnection.getArrowHead() != null) {
                    selectedConnection.getArrowHead().getStyleClass().add("selected-connection-arrow");
                }

                showConnectionDeleteButton(selectedConnection, line);
            }
        }
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
            // First, clear any previous selections
            for (VBox box : nodeBoxes.values()) {
                box.getStyleClass().remove("selected-node");
            }

            // Select the node
            selectNode(node);

            // Start drag operation
            draggedNode = node;
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();

            // Save initial position for undo
            oldNodeX = node.getX();
            oldNodeY = node.getY();

            e.consume(); // Prevent event bubbling
        });

        nodeBox.setOnMouseDragged(e -> {
            // Only process drag for the active dragged node
            if (draggedNode == node) {
                // Calculate offset considering scale
                double offsetX = (e.getSceneX() - dragStartX) / scale.get();
                double offsetY = (e.getSceneY() - dragStartY) / scale.get();

                // Update model position
                double newX = node.getX() + offsetX;
                double newY = node.getY() + offsetY;
                node.setPosition(newX, newY);

                // Get the current node box from the map to ensure we're using the right one
                VBox currentNodeBox = nodeBoxes.get(node.getId());

                // Update the visual position
                currentNodeBox.setLayoutX(newX);
                currentNodeBox.setLayoutY(newY);

                // Update for next drag event
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();

                // Update connections
                drawConnections();
            }

            e.consume(); // Prevent event bubbling
        });

        nodeBox.setOnMouseReleased(e -> {
            // End the drag operation
            if (draggedNode == node) {
                // Only record an undoable action if the node actually moved
                if (oldNodeX != node.getX() || oldNodeY != node.getY()) {
                    undoStack.push(new MoveNodeAction(node, oldNodeX, oldNodeY));
                }
                draggedNode = null;
            }
            e.consume(); // Prevent event bubbling
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

        Tooltip tooltip = new Tooltip(node.getDescription());
        tooltip.setShowDelay(javafx.util.Duration.millis(250));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300); // Set max width to ensure proper wrapping
        tooltip.getStyleClass().add("node-tooltip");
        Tooltip.install(nodeBox, tooltip);
    }

    private void drawConnections() {
        // Only remove standard connections (lines), but preserve custom connections
        canvas.getChildren().removeIf(node ->
                (node instanceof Line && !node.getStyleClass().contains("custom-connection-line")) ||
                        (node instanceof HBox && node.getStyleClass().contains("decision-tag") &&
                                !node.getStyleClass().contains("custom-decision-tag")));

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
        final double positionRatio = 0.56;
        final double posX = startX + positionRatio * (endX - startX);
        final double posY = startY + positionRatio * (endY - startY);

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
        decisionTag.getStyleClass().addAll("decision-tag", "node-decision-tag"); // Add distinct class
        decisionTag.setAlignment(Pos.CENTER);

        // Set vertical position immediately
        decisionTag.setLayoutY(posY - 15);

        // Center consistently using a width property listener
        decisionTag.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            decisionTag.setLayoutX(posX - (newWidth.doubleValue() / 2));
        });

        // Initial positioning after UI is rendered
        Platform.runLater(() -> {
            decisionTag.setLayoutX(posX - (decisionTag.getWidth() / 2));
        });

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
                // No need to adjust position manually - the width listener handles it
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
        Color dotColor = Color.rgb(40, 46, 63); // #282830

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
        customConnections.clear();
        customDecisionTags.clear();
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
                // Clear existing nodes and connections
                nodes.clear();
                nodeBoxes.clear();
                customConnections.clear();
                customDecisionTags.clear();

                // Read nodes map
                Map<String, StoryNode> loadedNodes = (Map<String, StoryNode>) in.readObject();
                nodes.putAll(loadedNodes);

                // Read start node ID
                String startNodeId = (String) in.readObject();
                startNode = nodes.get(startNodeId);

                // Check for EOF to determine if this is a new format file with custom connections
                try {
                    // Attempt to read version marker or go directly to connections depending on implementation
                    Object nextObject = in.readObject();

                    // If this is a version marker (for future use)
                    if (nextObject instanceof Integer) {
                        int version = (Integer) nextObject;
                        // Handle different versions as needed in the future

                        // Read connections for version 2+ files
                        List<CustomConnection> loadedConnections = (List<CustomConnection>) in.readObject();
                        customConnections.addAll(loadedConnections);

                        // Read custom decision tags if they exist
                        try {
                            List<CustomDecisionTag> loadedTags = (List<CustomDecisionTag>) in.readObject();
                            customDecisionTags.addAll(loadedTags);
                        } catch (EOFException e) {
                            // No tags in file, that's ok
                        }
                    }
                    // If we directly stored connections (current implementation)
                    else if (nextObject instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<CustomConnection> loadedConnections = (List<CustomConnection>) nextObject;
                        customConnections.addAll(loadedConnections);

                        // Try to read custom decision tags
                        try {
                            List<CustomDecisionTag> loadedTags = (List<CustomDecisionTag>) in.readObject();
                            customDecisionTags.addAll(loadedTags);
                        } catch (EOFException e) {
                            // No tags in file, that's ok
                        }
                    }
                } catch (EOFException e) {
                    // This is an older file that doesn't have custom connections - that's ok
                    System.out.println("Loading pre-update adventure file (no custom connections)");
                } catch (Exception e) {
                    // Log other exceptions but continue with the data we have
                    System.err.println("Warning: Error reading custom connections: " + e.getMessage());
                }

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

    private void undo() {
        if (!undoStack.isEmpty()) {
            UndoableAction action = undoStack.pop();
            action.undo();
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
            // Show saving indicator with fake progress
            showSavingIndicator();

            // Create a final copy of the file for use in the lambda
            final File savedFile = file;

            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                // Write nodes map
                out.writeObject(nodes);

                // Write start node ID
                out.writeObject(startNode.getId());

                // Write custom connections
                out.writeObject(customConnections);

                // Write custom decision tags
                out.writeObject(customDecisionTags);

                currentFileName = file.getAbsolutePath();

                // Use a simple timer to hide the indicator after 200ms
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        javafx.application.Platform.runLater(() -> {
                            statusLabel.setText("Adventure Game Planner - " + savedFile.getName());
                            hideSavingIndicator();
                        });
                        timer.cancel();
                    }
                }, 200);

            } catch (Exception e) {
                hideSavingIndicator();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Saving File");
                alert.setHeaderText("Could not save the adventure file.");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    // Setup status bar method
    private void setupStatusBar() {
        // Create progress bar for saving indicator
        savingProgressBar = new ProgressBar();
        savingProgressBar.setPrefWidth(100);
        savingProgressBar.setVisible(false); // Initially hidden
        savingProgressBar.getStyleClass().add("saving-progress");

        // Update the status bar creation to include the progress bar
        ((HBox)statusLabel.getParent()).getChildren().add(savingProgressBar);
        HBox.setHgrow(statusLabel, Priority.ALWAYS); // Make label take all available space
        HBox.setMargin(savingProgressBar, new Insets(0, 10, 0, 0)); // Add right margin
    }

    // Simplified indicator methods
    private void showSavingIndicator() {
        statusLabel.setText("Saving...");
        savingProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        savingProgressBar.setVisible(true);
    }

    private void hideSavingIndicator() {
        savingProgressBar.setVisible(false);
    }

    private void setupCanvasClickHandler() {
        canvas.setOnMouseClicked(e -> {
            // Only clear selection if we're clicking on the canvas itself
            // and not on a node or something else
            if (e.getTarget() == canvas && !decisionTagModeActive) {
                clearConnectionSelection();
            }
        });
    }

    @Override
    public void stop() {
        // Cancel any running timers
        // If you have any Timer objects as class fields, cancel them here

        // Force exit the application
        Platform.exit();
        System.exit(0);
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

    // Custom Connection class
    public static class CustomConnection implements Serializable {
        private static final long serialVersionUID = 1L;

        private double startX, startY;
        private double endX, endY;

        // Transient fields for UI elements (not serialized)
        private transient Line line;
        private transient Line selectionLine;
        private transient javafx.scene.shape.Polygon arrowHead;

        public CustomConnection(double startX, double startY, double endX, double endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public double getStartX() { return startX; }
        public double getStartY() { return startY; }
        public double getEndX() { return endX; }
        public double getEndY() { return endY; }

        public Line getLine() { return line; }
        public void setLine(Line line) { this.line = line; }

        public Line getSelectionLine() { return selectionLine; }
        public void setSelectionLine(Line selectionLine) { this.selectionLine = selectionLine; }

        public javafx.scene.shape.Polygon getArrowHead() { return arrowHead; }
        public void setArrowHead(javafx.scene.shape.Polygon arrowHead) { this.arrowHead = arrowHead; }
    }

    // Interface for undoable actions
    private interface UndoableAction {
        void undo();
    }

    // Node movement undo action
    private class MoveNodeAction implements UndoableAction {
        private StoryNode node;
        private double oldX, oldY;

        public MoveNodeAction(StoryNode node, double oldX, double oldY) {
            this.node = node;
            this.oldX = oldX;
            this.oldY = oldY;
        }

        @Override
        public void undo() {
            // Store current position in case we want to redo later
            double currentX = node.getX();
            double currentY = node.getY();

            // Restore old position
            node.setPosition(oldX, oldY);

            // Update visual position
            VBox nodeBox = nodeBoxes.get(node.getId());
            if (nodeBox != null) {
                nodeBox.setLayoutX(oldX);
                nodeBox.setLayoutY(oldY);
            }

            // Update connections
            drawConnections();
        }
    }

    private class DeleteConnectionAction implements UndoableAction {
        private CustomConnection connection;

        public DeleteConnectionAction(CustomConnection connection) {
            this.connection = connection;
        }

        @Override
        public void undo() {
            // Add the connection back
            customConnections.add(connection);

            // Redraw the connection
            drawCustomConnection(connection);
        }
    }

    // Connection creation undo action
    private class AddConnectionAction implements UndoableAction {
        private CustomConnection connection;

        public AddConnectionAction(CustomConnection connection) {
            this.connection = connection;
        }

        @Override
        public void undo() {
            customConnections.remove(connection);
            updateCanvas();
        }
    }

    // Custom Decision Tag class
    public static class CustomDecisionTag implements Serializable {
        private static final long serialVersionUID = 1L;

        private double x, y;
        private String text;

        // Transient field for UI element (not serialized)
        private transient HBox tagBox;

        public CustomDecisionTag(double x, double y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public HBox getTagBox() { return tagBox; }
        public void setTagBox(HBox tagBox) { this.tagBox = tagBox; }
    }

    private class AddDecisionTagAction implements UndoableAction {
        private CustomDecisionTag tag;

        public AddDecisionTagAction(CustomDecisionTag tag) {
            this.tag = tag;
        }

        @Override
        public void undo() {
            customDecisionTags.remove(tag);
            if (tag.getTagBox() != null) {
                canvas.getChildren().remove(tag.getTagBox());
            }
        }
    }

    private class DeleteDecisionTagAction implements UndoableAction {
        private CustomDecisionTag tag;

        public DeleteDecisionTagAction(CustomDecisionTag tag) {
            this.tag = tag;
        }

        @Override
        public void undo() {
            customDecisionTags.add(tag);
            drawCustomDecisionTag(tag);
        }
    }

    private class MoveDecisionTagAction implements UndoableAction {
        private CustomDecisionTag tag;
        private double oldX, oldY;

        public MoveDecisionTagAction(CustomDecisionTag tag, double oldX, double oldY) {
            this.tag = tag;
            this.oldX = oldX;
            this.oldY = oldY;
        }

        @Override
        public void undo() {
            // Store current position for potential redo
            double currentX = tag.getX();
            double currentY = tag.getY();

            // Restore old position
            tag.x = oldX;
            tag.y = oldY;

            // Update visual position
            if (tag.getTagBox() != null) {
                tag.getTagBox().setLayoutX(oldX);
                tag.getTagBox().setLayoutY(oldY);
            }
        }
    }

    private class EditDecisionTagAction implements UndoableAction {
        private CustomDecisionTag tag;
        private String oldText;

        public EditDecisionTagAction(CustomDecisionTag tag, String oldText) {
            this.tag = tag;
            this.oldText = oldText;
        }

        @Override
        public void undo() {
            // Store current text
            String currentText = tag.getText();

            // Restore old text
            tag.setText(oldText);

            // Update visual text
            if (tag.getTagBox() != null) {
                // Find the label in the HBox
                for (Node node : tag.getTagBox().getChildren()) {
                    if (node instanceof Label) {
                        ((Label) node).setText(oldText);
                        break;
                    }
                }
            }

            // For future redo implementation
            oldText = currentText;
        }
    }
}