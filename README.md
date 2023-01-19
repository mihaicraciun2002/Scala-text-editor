# Scala-text-editor
Scala text editor
Tested on scala 2.13

To compile:
scalac *.scala
To run:
scala UndoableEditor

or, for editor without undo,

scala Editor

///////////////////////////

Functionality:

LEFT, Ctrl-B Move one character left.

RIGHT, Ctrl-F Move one character right.

UP, Ctrl-P Move to the previous line.

DOWN, Ctrl-N Move to the next line.

HOME, Ctrl-A Move to the start of the line.

END, Ctrl-E Move to the end of the line.

PAGEUP Move up one screen.

PAGEDOWN Move down one screen.

Backspace Delete the character to the left.

DELETE, Ctrl-D Delete the character to the right.

Ctrl-G Abort the current command.

Ctrl-L Redraw and recentre the display.

Ctrl-Q Quit the editor.

Ctrl-R Read in a different file.

Ctrl-W Write the buffer to a file.

Ctrl-Z Undo the most recent change.

Ctrl-Y Redo the most recently undone change.

Ctrl-HOME Move to the start of the buffer.

Ctrl-END Move to the end of the buffer.

Ctrl-T Transpose two characters.

Ctrl-M Place the mark at the current point.

Ctrl-O Swap point and mark.

Ctrl-X Cut the text between point and mark.

Ctrl-C Copy the text between point and mark.

Ctrl-V Paste the most recently cut or copied text.

Ctrl-J Search for a string.

Ctrl-S Interactive search for a string.

