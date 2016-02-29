
import java.awt.Container;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
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

import jwinpointer.JWinPointerReader;
import jwinpointer.JWinPointerReader.PointerEventListener;




public class MultitouchFramework
	extends GLJPanel
	implements KeyListener, MouseListener, MouseMotionListener, PointerEventListener, GLEventListener
{
	public static final int TOUCH_EVENT_DOWN = 0;
	public static final int TOUCH_EVENT_MOVE = 1;
	public static final int TOUCH_EVENT_UP = 2;

	private GraphicsWrapper gw = new GraphicsWrapper();
	private SimpleWhiteboard client = null;
	private Component rootComponent = null; // used to convert between coordinate systems

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
	public void mousePressed( MouseEvent e ) {
		client.mousePressed(e);
	}
	public void mouseReleased( MouseEvent e ) {
		client.mouseReleased( e );
	}
	public void mouseMoved( MouseEvent e ) {
		client.mouseMoved( e );
	}
	public void mouseDragged( MouseEvent e ) {
		client.mouseDragged( e );
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
		// gl.glFlush(); // I don't think this is necessary
	}

	private static final int JWINPOINTER_EVENT_TYPE_DRAG = 1;
	private static final int JWINPOINTER_EVENT_TYPE_HOVER = 2;
	private static final int JWINPOINTER_EVENT_TYPE_DOWN = 3;
	private static final int JWINPOINTER_EVENT_TYPE_UP = 4;
	private static final int JWINPOINTER_EVENT_TYPE_BUTTON_DOWN = 5;
	private static final int JWINPOINTER_EVENT_TYPE_BUTTON_UP = 6;
	private static final int JWINPOINTER_EVENT_TYPE_IN_RANGE = 7;
	private static final int JWINPOINTER_EVENT_TYPE_OUT_OF_RANGE = 8;


	public void pointerXYEvent(int deviceType, int pointerID, int eventType, boolean inverted, int x, int y, int pressure) {
		//System.out.println("Pointer coordinates before conversion: "+x+","+y);
		Point p = SwingUtilities.convertPoint(rootComponent, x, y, this);
		x = p.x;
		y = p.y;
		//System.out.println("Pointer coordinates: "+x+","+y);
		int touchEvent = 0;
		switch ( eventType ) {
		case JWINPOINTER_EVENT_TYPE_DRAG:
			touchEvent = TOUCH_EVENT_MOVE;
			break;
		case JWINPOINTER_EVENT_TYPE_DOWN:
		case JWINPOINTER_EVENT_TYPE_BUTTON_DOWN:
			touchEvent = TOUCH_EVENT_DOWN;
			break;
		case JWINPOINTER_EVENT_TYPE_UP:
		case JWINPOINTER_EVENT_TYPE_BUTTON_UP:
			touchEvent = TOUCH_EVENT_UP;
			break;
		case JWINPOINTER_EVENT_TYPE_HOVER:
		case JWINPOINTER_EVENT_TYPE_IN_RANGE:
		case JWINPOINTER_EVENT_TYPE_OUT_OF_RANGE:
			return;
		}
		client.processMultitouchInputEvent( pointerID, x, y, touchEvent );
		repaint();
	}
	public void pointerButtonEvent(int deviceType, int pointerID, int eventType, boolean inverted, int buttonIndex) {
	}
	public void pointerEvent(int deviceType, int pointerID, int eventType, boolean inverted) {
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

		mf.rootComponent = mf;
		if ( Constant.HAS_MENUBAR ) {
			JMenuBar menuBar = mf.client.createMenuBar();
			if ( menuBar != null ) {
				frame.setJMenuBar(menuBar);
				mf.rootComponent = menuBar; // for some reason, when there's a menubar, the frame doesn't work properly for converting between coordinate systems, but the menubar does work for this
			}
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
			if ( panelOfWidgets != null ) {
				pane.add( panelOfWidgets, BorderLayout.LINE_START );
				if ( ! Constant.HAS_MENUBAR )
					mf.rootComponent = panelOfWidgets;
			}
		}
		pane.add( mf, BorderLayout.CENTER );

		frame.pack();
		frame.setVisible( true );

		mf.start();

		JWinPointerReader jWinPointerReader = new JWinPointerReader(frame);
		jWinPointerReader.addPointerEventListener(mf);
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


