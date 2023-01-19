
// Editor.scala
// Copyright (c) 2015 J. M. Spivey
// Amended 2017 by P.G. Jeavons

/** The controller class for a basic editor */
class Editor {
    /** The buffer being edited. */
    protected val ed = new EdBuffer

    /** The display. */
    protected var display: Display = null
    
    /** Whether the command loop should continue */
    private var alive = true

    private var copied : String = new String ()
    private var search : String = new String ()
    
    /** Show the buffer on a specified display */
    def activate(display: Display) {
        this.display = display
        display.show(ed)
        ed.register(display)
        ed.initDisplay()
    }

    /** Ask for confirmation if the buffer is not clean */
    def checkClean(action: String) = {
        if (! ed.isModified) 
            true
        else {
            val question = 
                "Buffer modified -- really %s?".format(action)
            MiniBuffer.ask(display, question)
        }
    }

    /** Load a file into the buffer */
    def loadFile(fname: String) = { ed.loadFile(fname); ed.point = 0; ed.update() }

    /** Command: Move the cursor in the specified direction */
    def moveCommand(dir: Int) {
        var p = ed.point
        val row = ed.getRow(p)

        dir match {
            case Editor.LEFT => 
                if (p > 0) p -= 1
            case Editor.RIGHT =>
                if (p < ed.length) p += 1
            case Editor.UP =>
                p = ed.getPos(row-1, goalColumn())
            case Editor.DOWN =>
                p = ed.getPos(row+1, goalColumn())
            case Editor.HOME =>
                p = ed.getPos(row, 0)
            case Editor.END =>
                p = ed.getPos(row, ed.getLineLength(row)-1)
            case Editor.PAGEDOWN =>
                p = ed.getPos(row + Editor.SCROLL, 0)
                display.scroll(+Editor.SCROLL)
            case Editor.PAGEUP =>
                p = ed.getPos(row - Editor.SCROLL, 0)
                display.scroll(-Editor.SCROLL)
            case Editor.CTRLHOME =>
                p = ed.getPos(0, 0)
            case Editor.CTRLEND =>
                p = ed.getPos (ed.numLines - 1, ed.getLineLength(ed.numLines - 1))
            case _ =>
                throw new Error("Bad direction for move")
        }

        ed.point = p
    }

    def transposeCommand(): Boolean = {
        if (ed.getColumn(ed.point) < ed.getLineLength(ed.getRow(ed.point)) - 1 &&
          ed.transpose(ed.point)) {
            ed.point += 1
            return true
        }
        return false
    }

    def placeMarkCommand() : Unit = {
        ed.mark = ed.point
        println(ed.mark + " " + ed.point)
    }

    def swapMarkCommand() : Unit = {
        val aux = ed.point
        ed.point = ed.mark
        ed.mark = aux
        println(ed.mark + " " + ed.point)
    }

    def copyCommand() : Unit = {
        var i = ed.point
        var j = ed.mark
        copied = new String()
        if(i > j) {
            i = ed.mark
            j = ed.point
        }
        while(i <= j)  {
            copied = copied + ed.charAt(i)
            i += 1
        }
        println(copied)
    }

    def cutCommand() : Unit = {
        var i = ed.point
        var ci = i
        var j = ed.mark
        copied = new String()
        if(i > j) {
            i = ed.mark
            j = ed.point
        }
        ci = i
        while(i <= j) {
            copied = copied + ed.charAt(i)
            i += 1
        }
        ed.point = ci
        ed.mark = ci
        ed.deleteRange(ci, j - ci + 1)
        println(ed.point + " " + ed.mark + " " + copied)
    }

    def pasteCommand(): Unit =  {
        if(ed.mark > ed.point)
            ed.mark += copied.length
        ed.insert(ed.point, copied)
        ed.point += copied.length
    }

    def searchCommand() : Unit = {
        search = MiniBuffer.readString(display, "Search:", ed.filename)
        var i = ed.point + 1
        print(search)
            while (i <= ed.length - search.length) {
                var j = i
                var ok: Boolean = true
                while (j < i + search.length && ok == true) {
                    if (ed.charAt(j) != search(j - i))
                        ok = false
                    j += 1
                }
                if (ok == true) {
                    ed.point = i
                    //print("i")
                    return
                }
                i += 1
            }
            throw new RuntimeException("String not found")
    }

    /** Command: Insert a character */
    def insertCommand(ch: Char) {
        ed.insert(ch)
        ed.point += 1
    }

    /** Command: Delete in a specified direction */
    def deleteCommand(dir: Int): Boolean = {
        var p = ed.point
 
        dir match {
            case Editor.LEFT =>
                if (p == 0) { beep(); return false }
                p -= 1
                ed.deleteChar(p)
                ed.point = p
            case Editor.RIGHT =>
                if (p == ed.length) { beep(); return false }
                ed.deleteChar(p)
            case _ =>
                throw new Error("Bad direction for delete")
        }
        return true
     }
    
    /** Command: Save the file */
    def saveFileCommand() {
        val name = 
            MiniBuffer.readString(display, "Write file", ed.filename)
        if (name != null && name.length > 0)
            ed.saveFile(name)
    }

    /** Prompt for a file to read into the buffer.  */
    def replaceFileCommand(): Boolean = {
        if (! checkClean("overwrite")) return false
        val name = 
            MiniBuffer.readString(display, "Read file", ed.filename)
        if (name != null && name.length > 0) {
            if (ed.loadFile(name)) { 
                ed.point = 0
                return true
            }
        }
        return false
    }

    /** Command: recenter and rewrite the display */
    def chooseOrigin() { 
        display.chooseOrigin() 
        ed.forceRewrite()
    }
    
    /** Quit, after asking about modified buffer */
    def quit() {
        if (checkClean("quit")) alive = false
    } 

    // Command execution protocol
    
    /** Goal column for vertical motion. */
    private var goal = -1
    private var prevgoal = 0
     
    /** Execute a command, wrapping it in actions common to all commands */
    protected def obey(cmd: Editor.Command) {
        prevgoal = goal; goal = -1
        display.setMessage(null)
        cmd(this)
        ed.update()
     }
    
    /** The desired column for the cursor after an UP or DOWN motion */
    private def goalColumn() = {        
        /* Successive UP and DOWN commands share the same goal column,
         * but other commands cause it to be reset to the current column */
        if (goal < 0) {
            val p = ed.point
            goal = if (prevgoal >= 0) prevgoal else ed.getColumn(p)
        }
        
        goal
    }

    /** Beep */
    def beep() { display.beep() }
 
    
    /** Read keystrokes and execute commands */
    def commandLoop() {
        //activate(display)

        while (alive) {
            val key = display.getKey()
            Editor.keymap.find(key) match {
                case Some(cmd) => obey(cmd)
                case None => beep()
            }
        }
    }
}

object Editor {
    /** Direction for use as argument to moveCommand or deleteCommand. */
    val LEFT = 1
    val RIGHT = 2
    val UP = 3
    val DOWN = 4
    val HOME = 5
    val END = 6
    val PAGEUP = 7
    val PAGEDOWN = 8
    val CTRLHOME = 9
    val CTRLEND = 10
    /** Amount to scroll the screen for PAGEUP and PAGEDOWN */
    val SCROLL = Display.HEIGHT - 3

    /** Possible value for damage. */
    val CLEAN = 0
    val REWRITE_LINE = 1
    val REWRITE = 2

    /** Keymap for editor commands */
    type Command = (Editor => Boolean)
    
    // This implicit conversion allows methods that return Unit to
    // be used as commands, that always succeed
    import scala.language.implicitConversions
    implicit def fixup(v: Unit): Boolean = true

    
    val keymap = Keymap[Command](
        Display.RETURN -> (_.insertCommand('\n')),
        Display.CTRLHOME -> (_.moveCommand(CTRLHOME)),
        Display.CTRLEND -> (_.moveCommand(CTRLEND)),
        Display.RIGHT -> (_.moveCommand(RIGHT)),
        Display.LEFT -> (_.moveCommand(LEFT)),
        Display.UP -> (_.moveCommand(UP)),
        Display.DOWN -> (_.moveCommand(DOWN)),
        Display.HOME -> (_.moveCommand(HOME)),
        Display.END -> (_.moveCommand(END)),
        Display.PAGEUP -> (_.moveCommand(PAGEUP)),
        Display.PAGEDOWN -> (_.moveCommand(PAGEDOWN)),
        Display.ctrl('?') -> (_.deleteCommand(LEFT)),
        Display.DEL -> (_.deleteCommand(RIGHT)),
        Display.ctrl('A') -> (_.moveCommand(HOME)),
        Display.ctrl('B') -> (_.moveCommand(LEFT)),
        Display.ctrl('D') -> (_.deleteCommand(RIGHT)),
        Display.ctrl('E') -> (_.moveCommand(END)),
        Display.ctrl('F') -> (_.moveCommand(RIGHT)),
        Display.ctrl('G') -> (_.beep),
        Display.ctrl('L') -> (_.chooseOrigin),
        Display.ctrl('N') -> (_.moveCommand(DOWN)),
        Display.ctrl('P') -> (_.moveCommand(UP)),
        Display.ctrl('Q') -> (_.quit),
        Display.ctrl('R') -> (_.replaceFileCommand),
        Display.ctrl('W') -> (_.saveFileCommand),
        Display.ctrl('T') -> (_.transposeCommand()),
        Display.ctrl('M') -> (_.placeMarkCommand()),
        Display.ctrl('O') -> (_.swapMarkCommand()),
        Display.ctrl('C') -> (_.copyCommand()),
        Display.ctrl('X') -> (_.cutCommand()),
        Display.ctrl('V') -> (_.pasteCommand()),
        Display.ctrl('J') -> (_.searchCommand())
        )

    for (ch <- Display.printable)
        keymap += ch -> (_.insertCommand(ch.toChar))
        
    /** Main program for the entire Ewoks application. */
    def main(args: Array[String]) {
        // Check number of arguments
        if (args.length > 1) {
            Console.err.println("Usage: ewoks [file]")
            scala.sys.exit(2)
        }

        // Initial setup
        val terminal = new Terminal("EWOKS")
        terminal.activate()
        val display = new Display(terminal)
        val app = new Editor()
        app.activate(display)
        if (args.length > 0) app.loadFile(args(0))
        
        // Main execution loop
        app.commandLoop()
        scala.sys.exit(0)
    }
}
