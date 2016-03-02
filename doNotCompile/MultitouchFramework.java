
import java.util.ArrayList;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
//import javax.swing.BoxLayout;

//import javax.media.opengl.GL;
import javax.media.opengl.GLJPanel;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLAutoDrawable;
// import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
//import com.sun.opengl.util.GLUT;


class Finger {
	public static final int FINGER_RADIUS = 15;
	public Point2D position = new Point2D(); // in pixel coordinates
	public int id;
}
class FingerContainer {
	private ArrayList< Finger > fingers = new ArrayList< Finger >();

	public int getNumFingers() { return fingers.size(); }
	public Finger getFingerByIndex( int index ) { return fingers.get( index ); }

	// returns -1 if no finger is under the given position
	public int getIndexOfFingerUnderCursorPosition( int x, int y ) {
		for ( int i = fingers.size()-1; i >= 0; --i ) {
			Finger f = fingers.get(i);
			float dx = f.position.x() - x;
			float dy = f.position.y() - y;
			float distanceSquared = dx*dx + dy*dy;
			if ( distanceSquared < Finger.FINGER_RADIUS*Finger.FINGER_RADIUS )
				return i;
		}
		return -1;
	}
	// returns index of newly created finger
	public int createFinger( int x, int y ) {
		Finger f = new Finger();
		f.id = fingers.isEmpty() ? 0 : ( fingers.get(fingers.size()-1).id + 1 );
		f.position.get()[0] = x;
		f.position.get()[1] = y;
		fingers.add( f );
		return fingers.size()-1;
	}
	public void deleteFingerByIndex( int index ) {
		fingers.remove( index );
	}
	public void drawFingers(
		GraphicsWrapper gw,
		int indexOfFingerToHilite // -1 if no finger is to be hilited
	) {
		gw.enableAlphaBlending();
		gw.setCoordinateSystemToPixels();
		for ( int i = 0; i < fingers.size(); ++i ) {
			Finger f = fingers.get(i);
			gw.setColor( 0, 0.5f, 0.5f, i==indexOfFingerToHilite ? 0.8f : 0.35f );
			gw.drawCircle( f.position.x()-Finger.FINGER_RADIUS, f.position.y()-Finger.FINGER_RADIUS, Finger.FINGER_RADIUS );
		}
	}
}


public class MultitouchFramework
	extends GLJPanel
	implements KeyListener, MouseListener, MouseMotionListener, GLEventListener
{
	FingerContainer fingerContainer = new FingerContainer();
	private boolean isFingerDraggingModeActive = false;
	private int indexOfFingerUnderMouse = -1;
	private int mouse_x, mouse_y;
	public static final int TOUCH_EVENT_DOWN = 0;
	public static final int TOUCH_EVENT_MOVE = 1;
	public static final int TOUCH_EVENT_UP = 2;

	private GraphicsWrapper gw = new GraphicsWrapper();
	private SimpleWhiteboard client = null;

	private int preferredWidth, preferredHeight;
	private int width, height;

	public void setPreferredWindowSize( int w, int h ) {
		preferredWidth = w;
		preferredHeight = h;
	}

	private void createClient() {
		client = new SimpleWhiteboard(this,gw);
	}

	public MultitouchFramework( GLCapabilities caps ) {
		super( caps );

		createClient();

		addGLEventListener(this);
		addKeyListener( this );
		addMouseListener( this );
		addMouseMotionListener( this );
	}
	public Dimension getPreferredSize() {
		return new Dimension( preferredWidth, preferredHeight );
	}

	public void start() {
		client.startBackgroundWork();
	}

	public void stop() {
		client.stopBackgroundWork();
	}

	// NOTE: calling e.consume() within these methods
	// could prevent us from receiving key events,
	// so we don't call it.
	//
	public void keyPressed( KeyEvent e ) {
		client.keyPressed(e);
	}
	public void keyReleased( KeyEvent e ) {
		client.keyReleased(e);
	}
	public void keyTyped( KeyEvent e ) {
		client.keyTyped(e);
	}
	public void mouseEntered( MouseEvent e ) {
		client.mouseEntered(e);
	}
	public void mouseExited( MouseEvent e ) {
		client.mouseExited(e);
	}
	public void mouseClicked( MouseEvent e ) {
		client.mouseClicked(e);
	}
	private void updateHiliting( int x, int y ) {
		int newIndexOfFingerUnderMouse = fingerContainer.getIndexOfFingerUnderCursorPosition( x, y );
		if ( newIndexOfFingerUnderMouse != indexOfFingerUnderMouse ) {
			indexOfFingerUnderMouse = newIndexOfFingerUnderMouse;
			requestRedraw();
		}
	}
	public void mousePressed( MouseEvent e ) {
		mouse_x = e.getX();
		mouse_y = e.getY();
		if ( e.isControlDown() ) {
			if ( indexOfFingerUnderMouse == -1 ) {
				indexOfFingerUnderMouse = fingerContainer.createFinger( e.getX(), e.getY() );
				Finger f = fingerContainer.getFingerByIndex( indexOfFingerUnderMouse );
				client.processMultitouchInputEvent(
					f.id,
					f.position.x(),
					f.position.y(),
					TOUCH_EVENT_DOWN
				);
			}
			isFingerDraggingModeActive = true;
		}
		else {
			client.mousePressed(e);
		}
	}
	public void mouseReleased( MouseEvent e ) {
		mouse_x = e.getX();
		mouse_y = e.getY();
		if ( isFingerDraggingModeActive ) {
			if ( SwingUtilities.isRightMouseButton(e) ) {
				Finger f = fingerContainer.getFingerByIndex( indexOfFingerUnderMouse );
				client.processMultitouchInputEvent(
					f.id,
					f.position.x(),
					f.position.y(),
					TOUCH_EVENT_UP
				);
				fingerContainer.deleteFingerByIndex( indexOfFingerUnderMouse );
			}
			isFingerDraggingModeActive = false;
			indexOfFingerUnderMouse = -1;
			updateHiliting( e.getX(), e.getY() );
			requestRedraw();
		}
		else {
			client.mouseReleased( e );
		}
	}
	public void mouseMoved( MouseEvent e ) {
		mouse_x = e.getX();
		mouse_y = e.getY();
		client.mouseMoved( e );
		updateHiliting( e.getX(), e.getY() );
	}
	public void mouseDragged( MouseEvent e ) {
		if ( isFingerDraggingModeActive ) {
			Finger f = fingerContainer.getFingerByIndex( indexOfFingerUnderMouse );
			f.position.get()[0] += e.getX() - mouse_x;
			f.position.get()[1] += e.getY() - mouse_y;
			client.processMultitouchInputEvent(
				f.id,
				f.position.x(),
				f.position.y(),
				TOUCH_EVENT_MOVE
			);
		}
		else {
			client.mouseDragged( e );
		}
		mouse_x = e.getX();
		mouse_y = e.getY();
	}

	public void requestRedraw() {
		repaint();
	}

	public void init( GLAutoDrawable drawable ) {
		gw.set( drawable );
	}
	public void reshape(
		GLAutoDrawable drawable,
		int x, int y, int width, int height
	) {
		gw.set( drawable );
		gw.resize( width, height );
	}
	public void displayChanged(
		GLAutoDrawable drawable,
		boolean modeChanged,
		boolean deviceChanged
	) {
		// leave this empty
	}
	public void display( GLAutoDrawable drawable ) {
		gw.set( drawable );
		client.draw();
		fingerContainer.drawFingers( gw, indexOfFingerUnderMouse );
		// gl.glFlush(); // I don't think this is necessary
	}

	// For thread safety, this should be invoked
	// from the event-dispatching thread.
	//
	private static void createUI() {
		if ( ! SwingUtilities.isEventDispatchThread() ) {
			System.out.println(
				"Warning: UI is not being created in the Event Dispatch Thread!");
			assert false;
		}

		GLCapabilities caps = new GLCapabilities();
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		MultitouchFramework mf = new MultitouchFramework( caps );

		JFrame frame = new JFrame( Constant.PROGRAM_NAME );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		if ( Constant.HAS_MENUBAR ) {
			JMenuBar menuBar = mf.client.createMenuBar();
			if ( menuBar != null )
				frame.setJMenuBar(menuBar);
		}

		// Need to set visible first before starting the rendering thread due
		// to a bug in JOGL. See JOGL Issue #54 for more information on this
		// https://jogl.dev.java.net/issues/show_bug.cgi?id=54
		frame.setVisible(true);

		Container pane = frame.getContentPane();
		// We used to use a BoxLayout as the layout manager here,
		// but it caused problems with resizing behavior due to
		// a JOGL bug https://jogl.dev.java.net/issues/show_bug.cgi?id=135
		pane.setLayout( new BorderLayout() );
		if ( Constant.HAS_PANEL_OF_WIDGETS ) {
			JPanel panelOfWidgets = mf.client.createPanelOfWidgets();
			if ( panelOfWidgets != null )
				pane.add( panelOfWidgets, BorderLayout.LINE_START );
		}
		pane.add( mf, BorderLayout.CENTER );

		frame.pack();
		frame.setVisible( true );

		mf.start();
	}

	public static void main( String[] args ) {
		// Schedule the creation of the UI for the event-dispatching thread.
		javax.swing.SwingUtilities.invokeLater(
			new Runnable() {
				public void run() {
					createUI();
				}
			}
		);
	}

}


