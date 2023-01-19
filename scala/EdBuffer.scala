

// EdBuffer.scala
// Copyright (c) 2015 J. M. Spivey
// Amended 2017 by P.G. Jeavons

import java.io.{Reader, Writer, FileReader, FileWriter, IOException}

/** The state of an editing session
 *  (this class is the model part of the MVC architecture) */
class EdBuffer {
    /** The text being edited. */
    private val text = new PlaneText()

    /** The display. */
    private var display: Display = null

    /** Register a display */
    def register(display: Display) { this.display = display }

    // State components that are preserved by undo and redo

    /** Current editing position. */
    private var _point = 0

    /** Mark position */
    private var _mark = 0

    // State components that are not restored on undo

    /** File name for saving the text. */
    private var _filename = ""

    /** Dirty flag */
    private var modified = false

    /** Mark the buffer as modified */
    private def setModified() { modified = true }

    /** Test whether the text is modified */
    def isModified = modified


    // Display update

    /** Extent that the display is out of date. */
    private var damage = EdBuffer.CLEAN

    /** If damage = REWRITE_LINE, the line that should be rewritten */
    private var damage_line = 0

    /** Note damage to the display. */
    private def noteDamage(rewrite: Boolean) {
        val newdamage =
            if (rewrite) EdBuffer.REWRITE else EdBuffer.REWRITE_LINE
        damage = Math.max(damage, newdamage)
        damage_line = text.getRow(point)
    }

    /** Force a display rewrite at next update */
    def forceRewrite() { noteDamage(true) }

    /** Update display with cursor at current point */
    def update() { update(point) }

    /** Update display with cursor at specified position */
    def update(pos: Int) {
        display.refresh(damage, text.getRow(pos), text.getColumn(pos))
        damage = EdBuffer.CLEAN
    }

    /** Initialise display */
    def initDisplay() {
        noteDamage(true)
        update()
    }


    // Accessors

    def point = _point

    def point_=(point: Int) {
        if (damage == EdBuffer.REWRITE_LINE && getRow(point) != damage_line)
            damage = EdBuffer.REWRITE
        _point = point
    }

    def mark = _mark

    def mark_=(mrk : Int): Unit =  {
        _mark = mrk
    }
    def filename = _filename

    private def filename_=(filename: String) { _filename = filename }


    // Delegate methods for text

    def charAt(pos: Int) = text.charAt(pos)

    def getRow(pos: Int) = text.getRow(pos)

    def getColumn(pos: Int) = text.getColumn(pos)

    def getPos(row: Int, col: Int) = text.getPos(row, col)

    def length = text.length

    def getLineLength(row: Int) = text.getLineLength(row)

    def getRange(pos: Int, len: Int) = text.getRange(pos, len)

    def numLines = text.numLines

    def fetchLine(n: Int, buf: Text) { text.fetchLine(n, buf) }

    def writeFile(out: Writer) { text.writeFile(out) }


    // Mutator methods

    /** Delete a character */
    def deleteChar(pos: Int) {
        val ch = text.charAt(pos)
        noteDamage(ch == '\n' || getRow(pos) != getRow(point))
        if(mark >= pos)
            mark -= 1
        println(mark)
        text.deleteChar(pos)
        setModified()
    }

    def transpose(p : Int): Boolean =  {
        val pos = this.getColumn(p)
        val row = this.getRow(p)
        if(this.getLineLength(row) <= 2 || pos >= this.getLineLength(row) - 2) {
            print(this.getLineLength(row) + "\n")
           /// print("gotcha" + p + " " + row + " " + this.getLineLength(row) + "\n")
            return false/// I didn't want to throw an error because the user makes mistakes
        }              /// like ctrl-t at the end of the buffer or on a length 1 line
        val ch1 = text.charAt(p)
        val ch2 = text.charAt(p + 1)
        this.deleteRange(p, 2)
    //    print("WE COULD DO IT")
        this.insert(p, ch2)
        this.insert(p + 1, ch1)
        return true
    }
    /** Delete a range of characters. */
    def deleteRange(pos: Int, len: Int) {
        noteDamage(true)
        text.deleteRange(pos, len)
        var howMuch : Int = 0
        if(mark > pos + len)
            howMuch = len
        else if(mark >= pos)
            howMuch = mark - pos
        mark = mark - howMuch
        println(mark)
        setModified()
    }

    /** Insert at current value of point */
    def insert(a: Any) {
        a match {case ch: Char => {
                    insert(point, ch)
                 }
                 case s : String => {
                     insert(point, s)
                 }
        }
    }

    /** Insert a character at a specified position */
    def insert(pos: Int, ch: Char) {
        noteDamage(ch == '\n' || getRow(pos) != getRow(point))
        if(mark >= pos)
            mark += 1
        println(mark)
        text.insert(pos, ch)
        setModified()
    }

    /** Insert a string */
    def insert(pos: Int, s: String) {
        noteDamage(true)
        if(mark >= pos)
            mark += s.length
        println(mark)
        text.insert(pos, s)
        setModified()
    }

    /** Insert an immutable text. */
    def insert(pos: Int, s: Text.Immutable) {
        noteDamage(true)
        if(mark >= pos)
            mark += s.length
        println(mark)
        text.insert(pos, s)
        setModified()
    }

    /** Insert a Text. */
    def insert(pos: Int, t: Text) {
        noteDamage(true)
        if(mark >= pos)
            mark += t.length
        text.insert(pos, t)
        setModified()
    }

     /** Load a file into the buffer. */
    def loadFile(name: String): Boolean = {
        filename = name

        try {
            val in = new FileReader(name)
            text.clear()
            text.insertFile(0, in)
            in.close()
            modified = false
            noteDamage(true)
            return true
        } catch {
            case e: IOException =>
                MiniBuffer.message(display, "Couldn't read file '%s'", name)
                return false
        }
    }

    /** Save buffer contents to a file */
    def saveFile(name: String): Boolean = {
        filename = name

        try {
            val out = new FileWriter(name)
            text.writeFile(out)
            out.close()
            modified = false
            return true
        } catch {
            case e: IOException =>
                MiniBuffer.message(display, "Couldn't write file '%s'", name)
                return false
        }
    }

    /** Make a Memento that records the current state */
    def getState() = new Memento()

    /** An immutable record of the editor state at some time.  The state that
     * is recorded consists of just the current point. */

    // it now also consists of the mark
    class Memento {
        private val pt = point
        private val mk = mark

        /** Restore the state when the memento was created */
        def restore() { point = pt; mark = mk }
    }


}

object EdBuffer {
    /** Possible value for damage. */
    val CLEAN = 0
    val REWRITE_LINE = 1
    val REWRITE = 2
}
