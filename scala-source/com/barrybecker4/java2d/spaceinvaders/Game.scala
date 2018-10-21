package com.barrybecker4.java2d.spaceinvaders

import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferStrategy
import com.barrybecker4.java2d.spaceinvaders.entity.{AlienEntity, Entity, ShipEntity, ShotEntity}
import javax.swing.JFrame
import javax.swing.JPanel


/**
  * This class with both act as a manager for the display and central mediator for the game logic.
  *
  * Display management will consist of a loop that cycles over all entities in the game asking them to move, and then
  * drawing them in the appropriate place. The KeyInputHandler class allows the player to control the main ship.
  *
  * As a mediator it will be informed when entities within our game detect events (e.g. alien killed, played died)
  * and will take appropriate game actions.
  *
  * @author Kevin Glass
  * @author Barry Becker (ported it to Scala)
  */
object Game extends App {
  // Entry point into the game. Simply create an instance of class which will start the display and game loop.
  val g = new Game
  // Start the main game loop. This method will not return until the game has finished running.
  // Hence we are using the actual main thread to run the game.
  g.gameLoop()
}

class Game() extends Canvas {
  // create a frame to contain our game
  val container = new JFrame("Space Invaders 101")

  /** The stragey that allows us to use accelerate page flipping */
  private var strategy: BufferStrategy = _
  /** True if the game is currently "running", i.e. the game loop is looping */
  private val gameRunning = true
  /** The list of all the entities that exist in our game */
  private var entities: Seq[Entity] = Seq()
  /** The list of entities that need to be removed from the game this loop */
  private var removeList: Set[Entity] = Set()
  /** The entity representing the player */
  private var ship: Entity = _
  /** The speed at which the player's ship should move (pixels/sec) */
  private val moveSpeed = 300
  /** The time at which last fired a shot */
  private var lastFire: Long = 0
  /** The interval between our players shot (ms) */
  private val firingInterval = 500
  /** The number of aliens left on the screen */
  private var alienCount = 0
  /** The message to display which waiting for a key press */
  private var message = ""
  /** Handlers user key input */
  private val keyHandler = new KeyInputHandler()
  /** True if game logic needs to be applied this loop, normally as a result of a game event */
  private var logicRequiredThisLoop = false

  // get hold the content of the frame and set up the resolution of the game
  val panel: JPanel = container.getContentPane.asInstanceOf[JPanel]
  panel.setPreferredSize(new Dimension(800, 600))
  panel.setLayout(null)
  // setup our canvas size and put it into the content of the frame
  setBounds(0, 0, 800, 600)
  panel.add(this)
  // Tell AWT not to bother repainting our canvas since we're going to do that our self in accelerated mode.
  setIgnoreRepaint(true)
  container.pack()
  container.setResizable(false)
  container.setVisible(true)

  // Add a listener to respond to the user closing the window. If they do we'd like to exit the game.
  container.addWindowListener(new WindowAdapter() {
    override def windowClosing(e: WindowEvent): Unit = {
      System.exit(0)
    }
  })
  // add a key input system (defined below) to our canvas
  // so we can respond to key pressed
  addKeyListener(keyHandler)
  // request the focus so key events come to us
  requestFocus()
  // create the buffering strategy which will allow AWT
  // to manage our accelerated graphics
  createBufferStrategy(2)
  strategy = getBufferStrategy
  // initialise the entities in our game so there's somethingto see at startup
  initEntities()

  /** Start a fresh game, this should clear out any old data andcreate a new set. */
  private def startGame(): Unit = { // clear out any existing entities and intialize a new set
    entities = Seq()
    initEntities()
    // blank out any keyboard settings we might currently have
    keyHandler.reset()
  }

  /** Initialise the starting state of the entities (ship and aliens).
    * EAah entity will be added to the overall list of entities in the game.
    */
  private def initEntities(): Unit = { // create the player ship and place it roughly in the center of the screen
    ship = new ShipEntity(this, "sprites/ship.gif", 370, 550)
    entities :+= ship
    // create a block of aliens (5 rows, by 12 aliens, spaced evenly)
    alienCount = 0
    var row = 0
    while (row < 5) {
      var x = 0
      while (x < 12) {
        val alien = new AlienEntity(this, "sprites/alien.gif", 100 + (x * 50), 50 + row * 30)
        entities :+= alien
        alienCount += 1
        x += 1
      }
      row += 1
    }
  }

  /** Notification from a game entity that the logic of the game
    * should be run at the next opportunity (normally as a result of some game event)
    */
  def updateLogic(): Unit =
    logicRequiredThisLoop = true

  /** Remove an entity from the game. The entity removed will no longer move or be drawn.
    * @param entity The entity that should be removed
    */
  def removeEntity(entity: Entity): Unit =
    removeList += entity

  /** Notification that the player has died. */
  def notifyDeath(): Unit = {
    message = "Oh no! They got you, try again?"
    keyHandler.waitingForKeyPress = true
  }

  /** Notification that the player has won since all the aliensare dead. */
  def notifyWin(): Unit = {
    message = "Well done! You Win!"
    keyHandler.waitingForKeyPress = true
  }

  /** Notification that an alien has been killed */
  def notifyAlienKilled(): Unit = {
    // reduce the alien count, if there are none left, the player has won!
    alienCount -= 1
    if (alienCount == 0) notifyWin()
    // if there are still some aliens left then they all need to get faster, so speed up all the existing aliens
    for(e <- entities)
      e.setHorizontalMovement(e.getHorizontalMovement * 1.05)
  }

  /** Attempt to fire a shot from the player. Its called "try"
    * since we must first check that the player can fire at this
    * point, i.e. has he/she waited long enough between shots
    */
  def tryToFire(): Unit = { // check that we have waiting long enough to fire
    if (System.currentTimeMillis - lastFire < firingInterval) return
    // if we waited long enough, create the shot entity, and record the time.
    lastFire = System.currentTimeMillis
    val shot = new ShotEntity(this, "sprites/shot.gif", ship.getX + 10, ship.getY - 30)
    entities :+= shot
  }

  /** The main game loop. Play is responsible for the following activities:
    * <p>
    * - Working out the speed of the game loop to update moves
    * - Moving the game entities
    * - Drawing the screen contents (entities, text)
    * - Updating game events
    * - Checking Input
    * <p>
    */
  def gameLoop(): Unit = {
    var lastLoopTime = System.currentTimeMillis
    // keep looping round til the game ends
    while (gameRunning) {
      // The time delta will be used to calculate how far the entities should move this iteration
      val delta = System.currentTimeMillis - lastLoopTime
      lastLoopTime = System.currentTimeMillis

      if (keyHandler.keyPressedToStart) {
        startGame()
        keyHandler.waitingForKeyPress = false
      } else {
        gameStep(delta)
      }
      // This should run us at about 100 fps.
      // Thread.sleep(10)
    }
  }

  def gameStep(timeStep: Long): Unit = {

    // Get hold of a graphics context for the accelerated
    // surface and blank it out
    val g = strategy.getDrawGraphics.asInstanceOf[Graphics2D]
    g.setColor(Color.black)
    g.fillRect(0, 0, 800, 600)
    // cycle round asking each entity to move itself
    if (!keyHandler.waitingForKeyPress) {
      var i = 0
      while (i < entities.size) {
        entities(i).move(timeStep)
        i += 1
      }
    }
    // cycle round drawing all the entities we have in the game
    var i = 0
    while (i < entities.size) {
      entities(i).draw(g)
      i += 1
    }

    checkForCollisions()

    // remove any entity that has been marked for clear up
    entities = entities.filter(e => !removeList.contains(e))

    removeList = Set()
    // If a game event has indicated that game logic should be resolved, cycle round every entity requesting that
    // their personal logic should be considered.
    if (logicRequiredThisLoop) {
      entities.foreach(e => e.doLogic())
      logicRequiredThisLoop = false
    }
    // if we're waiting for an "any key" press then draw the current message
    if (keyHandler.waitingForKeyPress) {
      g.setColor(Color.white)
      g.drawString(message, (800 - g.getFontMetrics.stringWidth(message)) / 2, 250)
      g.drawString("Press any key", (800 - g.getFontMetrics.stringWidth("Press any key")) / 2, 300)
    }
    // finally, we've completed drawing so clear up the graphics and flip the buffer over
    g.dispose()
    strategy.show()
    // Resolve the movement of the ship. Move to left or right if needed.
    ship.setHorizontalMovement(0)
    if (keyHandler.isLeftPressed) ship.setHorizontalMovement(-moveSpeed)
    else if (keyHandler.isRightPressed) ship.setHorizontalMovement(moveSpeed)
    // if we're pressing fire, attempt to fire
    if (keyHandler.isFirePressed) tryToFire()
  }

  /** If the bullet or ship collide with any aliens, notify both entities that the collision has occurred. */
  private def checkForCollisions(): Unit = {
    val (aliens, friendlies) = entities.partition(_.isInstanceOf[AlienEntity])

    friendlies.foreach(me => {
      aliens.foreach(him => {
        if (me.collidesWith(him)) {
          me.collidedWith(him)
          him.collidedWith(me)
        }
      })
    })
  }
}