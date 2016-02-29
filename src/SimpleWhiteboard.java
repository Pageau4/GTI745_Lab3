
import java.util.ArrayList;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
//import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;



// This stores a polygonal line, creating by a stroke of the user's finger or pen.
class Stroke {
	// the points that make up the stroke, in world space coordinates
	private ArrayList< Point2D > points = new ArrayList< Point2D >();

	private float color_red = 0;
	private float color_green = 0;
	private float color_blue = 0;

	private AlignedRectangle2D boundingRectangle = new AlignedRectangle2D();
	private boolean isBoundingRectangleDirty = false;

	public void addPoint( Point2D p ) {
		points.add( p );
		isBoundingRectangleDirty = true;
	}
	public ArrayList< Point2D > getPoints() {
		return points;
	}

	public void setColor( float r, float g, float b ) {
		color_red = r;
		color_green = g;
		color_blue = b;
	}

	public AlignedRectangle2D getBoundingRectangle() {
		if ( isBoundingRectangleDirty ) {
			boundingRectangle.clear();
			for ( Point2D p : points ) {
				boundingRectangle.bound( p );
			}
			isBoundingRectangleDirty = false;
		}
		return boundingRectangle;
	}
	public void markBoundingRectangleDirty() {
		isBoundingRectangleDirty = true;
	}

	public boolean isContainedInRectangle( AlignedRectangle2D r ) {
		return r.contains( getBoundingRectangle() );
	}
	public boolean isContainedInLassoPolygon( ArrayList< Point2D > polygonPoints ) {
		for ( Point2D p : points ) {
			if ( ! Point2DUtil.isPointInsidePolygon( polygonPoints, p ) )
				return false;
		}
		return true;
	}

	public void draw( GraphicsWrapper gw ) {
		gw.setColor( color_red, color_green, color_blue );
		gw.drawPolyline( points );
	}
}


// This stores a set of strokes.
// Even if there are multiple users interacting with the window at the same time,
// they all interect with a single instance of this class.
class Drawing {

	public ArrayList< Stroke > strokes = new ArrayList< Stroke >();

	private AlignedRectangle2D boundingRectangle = new AlignedRectangle2D();
	private boolean isBoundingRectangleDirty = false;

	public void addStroke( Stroke s ) {
		strokes.add( s );
		isBoundingRectangleDirty = true;
	}

	public AlignedRectangle2D getBoundingRectangle() {
		if ( isBoundingRectangleDirty ) {
			boundingRectangle.clear();
			for ( Stroke s : strokes ) {
				boundingRectangle.bound( s.getBoundingRectangle() );
			}
			isBoundingRectangleDirty = false;
		}
		return boundingRectangle;
	}
	public void markBoundingRectangleDirty() {
		isBoundingRectangleDirty = true;
	}

	public void draw( GraphicsWrapper gw ) {
		gw.setLineWidth( 5 );
		for ( Stroke s : strokes ) {
			s.draw( gw );
		}
		gw.setLineWidth( 1 );
	}

}

// This class stores the current position of a finger,
// as well as the history of previous positions of that finger
// during its drag.
//
// An instance of this class is created when a finger makes contact
// with the multitouch surface.  The instance stores all
// subsequent positions of the finger, and is destroyed
// when the finger is lifted off the multitouch surface.
class MyCursor {

	// Each finger in contact with the multitouch surface is given
	// a unique id by the framework (or computing platform).
	// There is no guarantee that these ids will be consecutive nor increasing.
	// For example, when two fingers are in contact with the multitouch surface,
	// their ids may be 0 and 1, respectively,
	// or their ids may be 14 and 9, respectively.
	public int id; // identifier

	// This stores the history of positions of the "cursor" (finger)
	// in pixel coordinates.
	// The first position is where the finger pressed down,
	// and the last position is the current position of the finger.
	private ArrayList< Point2D > positions = new ArrayList< Point2D >();

	private float totalDistanceAlongDrag = 0;
	private float distanceFromStartToEndOfDrag = 0;


	// These are used to store what the cursor is being used for.
	public static final int TYPE_NOTHING = 0; // in this case, the cursor is ignored
	public static final int TYPE_INTERACTING_WITH_WIDGET = 1; // interacting with a virtual button, menu, etc.
	public static final int TYPE_INKING = 2; // creating a stroke
	public static final int TYPE_CAMERA_PAN_ZOOM = 3;
	public static final int TYPE_SELECTION = 4;
	public static final int TYPE_DIRECT_MANIPULATION = 5;
	public int type = TYPE_NOTHING;


	// This is only used if (type == TYPE_INTERACTING_WITH_WIDGET),
	// in which case it stores the index of the palette button under the cursor.
	public int indexOfButton = 0;


	public MyCursor( int id, float x, float y ) {
		this.id = id;
		positions.add( new Point2D(x,y) );
	}

	public ArrayList< Point2D > getPositions() { return positions; }

	public void addPosition( Point2D p ) {
		if ( positions.size() >= 1 ) {
			totalDistanceAlongDrag += p.distance( positions.get(positions.size()-1) );
			distanceFromStartToEndOfDrag = p.distance( positions.get(0) );
		}
		positions.add( p );
	}

	public Point2D getFirstPosition() {
		if ( positions == null || positions.size() < 1 )
			return null;
		return positions.get( 0 );
	}
	public Point2D getCurrentPosition() {
		if ( positions == null || positions.size() < 1 )
			return null;
		return positions.get( positions.size()-1 );
	}
	public Point2D getPreviousPosition() {
		if ( positions == null || positions.size() == 0 )
			return null;
		if ( positions.size() == 1 )
			return positions.get( 0 );
		return positions.get( positions.size()-2 );
	}

	public boolean doesDragLookLikeLassoGesture() {
		return totalDistanceAlongDrag / (float)distanceFromStartToEndOfDrag > 2.5f;
	}

	public int getType() { return type; }
	public void setType( int type ) { this.type = type; }
	public void setType(
		int type,
		int indexOfButton // only used if (type == TYPE_INTERACTING_WITH_WIDGET)
	) {
		this.type = type;
		this.indexOfButton = indexOfButton;
	}
}


// This stores a set of instances of MyCursor.
// Each cursor can be identified by its id,
// which is assigned by the framework or computing platform.
// Each cursor can also be identified by its index in this class's container.
// For example, if an instance of this class is storing 3 cursors,
// their ids may be 2, 18, 7,
// but their indices should be 0, 1, 2.
class CursorContainer {
	private ArrayList< MyCursor > cursors = new ArrayList< MyCursor >();

	public int getNumCursors() { return cursors.size(); }
	public MyCursor getCursorByIndex( int index ) { return cursors.get( index ); }

	public int findIndexOfCursorById( int id ) {
		for ( int i = 0; i < cursors.size(); ++i ) {
			if ( cursors.get(i).id == id )
				return i;
		}
		return -1;
	}
	public MyCursor getCursorById( int id ) {
		int index = findIndexOfCursorById( id );
		return ( index == -1 ) ? null : cursors.get( index );
	}

	// Returns the number of cursors that are of the given type.
	public int getNumCursorsOfGivenType( int type ) {
		int num = 0;
		for ( int i = 0; i < cursors.size(); ++i ) {
			if ( cursors.get(i).getType() == type )
				num ++;
		}
		return num;
	}

	// Returns the (i)th cursor of the given type,
	// or null if no such cursor exists.
	// Can be used for retrieving both cursors of type TYPE_CAMERA_PAN_ZOOM, for example,
	// by calling getCursorByType( MyCursor.TYPE_CAMERA_PAN_ZOOM, 0 )
	// and getCursorByType( MyCursor.TYPE_CAMERA_PAN_ZOOM, 1 ),
	// when there may be cursors of other type present at the same time.
	public MyCursor getCursorByType( int type, int i ) {
		for ( int ii = 0; ii < cursors.size(); ++ii ) {
			if ( cursors.get(ii).getType() == type ) {
				if ( i == 0 )
					return cursors.get(ii);
				else
					i --;
			}
		}
		return null;
	}

	// Returns index of updated cursor.
	// If a cursor with the given id does not already exist, a new cursor for it is created.
	public int updateCursorById(
		int id,
		float x, float y
	) {
		Point2D updatedPosition = new Point2D( x, y );
		int index = findIndexOfCursorById( id );
		if ( index == -1 ) {
			cursors.add( new MyCursor( id, x, y ) );
			index = cursors.size() - 1;
		}
		MyCursor c = cursors.get( index );
		if ( ! c.getCurrentPosition().equals( updatedPosition ) ) {
			c.addPosition( updatedPosition );
		}
		return index;
	}
	public void removeCursorByIndex( int index ) {
		cursors.remove( index );
	}
	public ArrayList< Point2D > getWorldPositionsOfCursors( GraphicsWrapper gw ) {
		ArrayList< Point2D > positions = new ArrayList< Point2D >();
		for ( MyCursor cursor : cursors ) {
			positions.add( gw.convertPixelsToWorldSpaceUnits( cursor.getCurrentPosition() ) );
		}
		return positions;
	}
}





class PaletteButton {
	public static final int width = Constant.BUTTON_WIDTH; // in pixels
	public static final int height = Constant.BUTTON_HEIGHT; // in pixels
	public int x0, y0; // coordinates of upper left corner of button, in pixels, with respect to the upper left corner of the palette that contains us
	String label = "";
	String tooltip = "";

	public boolean isPressed = false; // if true, the button is drawn differently
	public boolean isSticky = false; // if true, the button remains pressed after the finger has lifted off (useful for modal or radio buttons)

	public PaletteButton( int x0, int y0, String label, String tooltip, boolean isSticky ) {
		this.x0 = x0;
		this.y0 = y0;
		this.label = label;
		this.tooltip = tooltip;
		this.isSticky = isSticky;
	}

	// returns bounding box in the local space of the palette
	public AlignedRectangle2D getBoundingRectangle() {
		return new AlignedRectangle2D( new Point2D(x0,y0), new Point2D(x0+width,y0+height) );
	}

	public boolean contains(
		float x, float y // pixel coordinates in the local space of the palette
	) {
		return getBoundingRectangle().contains( new Point2D(x,y) );
	}

	public void draw(
		int palette_x, int palette_y, // upper left corner of the palette that contains us, in pixels
		GraphicsWrapper gw
	) {
		// draw background
		if ( isPressed ) {
			gw.setColor( 0, 0, 0, Palette.alpha );
			gw.fillRect( palette_x + x0, palette_y + y0, width, height );
			// set the foreground color in preparation for drawing the label
			gw.setColor( 1, 1, 1 );
		}
		else {
			gw.setColor( 1, 1, 1, Palette.alpha );
			gw.fillRect( palette_x + x0, palette_y + y0, width, height );
			// draw border
			gw.setColor( 0, 0, 0 );
			gw.drawRect( palette_x + x0, palette_y + y0, width, height );
		}
		// draw text label
		int stringWidth = Math.round( gw.stringWidth( label ) );
		gw.drawString( palette_x + x0+(width-stringWidth)/2, palette_y + y0+height/2+Constant.TEXT_HEIGHT/2, label );
	}
}

class Palette {
	public int width, height; // in pixels
	public int x0, y0; // in pixels
	public static final float alpha = 0.3f; // if between 0 and 1, the palette is drawn semi-transparent

	public ArrayList< PaletteButton > buttons = null;

	// These variables are initialized in the contructor,
	// to save the index of each button,
	// but after that they should never change.
	public int movePalette_buttonIndex;
	public int ink_buttonIndex;
	public int select_buttonIndex;
	public int manipulate_buttonIndex;
	public int camera_buttonIndex;
	public int black_buttonIndex;
	public int red_buttonIndex;
	public int green_buttonIndex;
	public int horizFlip_buttonIndex;
	public int frameAll_buttonIndex;


	public int currentlyActiveModalButton; // could be equal to any of ink_buttonIndex, select_buttonIndex, manipulate_buttonIndex, camera_buttonIndex

	public int currentlyActiveColorButton; // could be equal to any of black_buttonIndex, red_buttonIndex, green_buttonIndex
	public float current_red = 0;
	public float current_green = 0;
	public float current_blue = 0;

	public Palette() {
		final int W = PaletteButton.width;
		final int H = PaletteButton.height;
		PaletteButton b = null;
		buttons = new ArrayList< PaletteButton >();


		// Create first row of buttons

		b = new PaletteButton(   0, 0, "Move", "Drag on this button to move the palette.", false );
		movePalette_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton(   W, 0, "Ink", "When active, use other fingers to draw ink strokes.", true );
		ink_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 2*W, 0, "Select", "When active, use another finger to select strokes.", true );
		select_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 3*W, 0, "Manip.", "When active, use one or two other fingers to directly manipulate the selection.", true );
		manipulate_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 4*W, 0, "Camera", "When active, use one or two other fingers to directly manipulate the camera.", true );
		camera_buttonIndex = buttons.size();
		buttons.add( b );


		// Create second row of buttons

		b = new PaletteButton(   0, H, "Black", "Changes ink color.", true );
		black_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton(   W, H, "Red", "Changes ink color.", true );
		red_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 2*W, H, "Green", "Changes ink color.", true );
		green_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 3*W, H, "Hor. Flip", "Flip the selection horizontally (around a vertical axis).", false );
		horizFlip_buttonIndex = buttons.size();
		buttons.add( b );

		b = new PaletteButton( 4*W, H, "Frame all", "Frames the entire drawing.", false );
		frameAll_buttonIndex = buttons.size();
		buttons.add( b );


		// Initialize remaining state

		buttons.get( ink_buttonIndex ).isPressed = true;
		currentlyActiveModalButton = ink_buttonIndex;
		buttons.get( black_buttonIndex ).isPressed = true;
		currentlyActiveColorButton = black_buttonIndex;
		current_red = current_green = current_blue = 0;

		// Figure out the width and height of the palette.
		// To do this, compute a bounding rectangle.
		AlignedRectangle2D boundingRectangle = new AlignedRectangle2D();
		for ( int j = 0; j < buttons.size(); ++j ) {
			boundingRectangle.bound( buttons.get(j).getBoundingRectangle() );
		}
		// Note that the bounding rectangle contains coordinates in the palette's local space.
		// We only store the width and height of the bounding rectangle.
		width = Math.round( boundingRectangle.getDiagonal().x() );
		height = Math.round( boundingRectangle.getDiagonal().y() );
	}
	public AlignedRectangle2D getBoundingRectangle() {
		return new AlignedRectangle2D( new Point2D(x0,y0), new Point2D(x0+width,y0+height) );
	}
	public Point2D getCenter() {
		return getBoundingRectangle().getCenter();
	}
	public boolean contains( float x, float y ) {
		return getBoundingRectangle().contains( new Point2D(x,y) );
	}
	// returns -1 if no button contains the given point
	public int indexOfButtonContainingTheGivenPoint( float x, float y ) {
		for ( int j = 0; j < buttons.size(); ++j ) {
			PaletteButton b = buttons.get( j );
			if ( b.contains( x-x0, y-y0 ) ) // the subtraction converts the coordinates to the palette's local coordinate system
				return j;
		}
		return -1;
	}

	public void draw( GraphicsWrapper gw ) {
		// draw border
		gw.setColor( 0, 0, 0 );
		gw.drawRect( x0, y0, width, height );

		for ( PaletteButton b : buttons ) {
			b.draw( x0, y0, gw );
		}
	}
}



class UserContext {
	private Palette palette = new Palette();
	private CursorContainer cursorContainer = new CursorContainer();
	private Drawing drawing = null;

	private ArrayList< Stroke > selectedStrokes = new ArrayList< Stroke >();

	public UserContext( Drawing d ) {
		drawing = d;
	}

	public void setPositionOfCenterOfPalette( float x, float y ) {
		palette.x0 = Math.round( x - palette.width/2 );
		palette.y0 = Math.round( y - palette.height/2 );
	}
	public void movePalette(
		float delta_x, float delta_y // displacement, in pixels
	) {
		palette.x0 += Math.round( delta_x );
		palette.y0 += Math.round( delta_y );
	}

	// returns true if any cursors are currently being handled by this user context
	public boolean hasCursors() {
		return cursorContainer.getNumCursors() > 0;
	}

	public int getNumCursors() {
		return cursorContainer.getNumCursors();
	}

	// returns true if the given cursor (identified by its id)
	// is currently being handled by this user context
	public boolean hasCursorID( int id ) {
		return cursorContainer.findIndexOfCursorById(id) > -1;
	}

	// returns the distance between the given point and the center of the palette of this user context
	public float distanceToPalette(
		float x, float y // pixel coordinates
	) {
		return Point2D.diff( palette.getCenter(), new Point2D(x,y) ).length();
	}


	public void draw( GraphicsWrapper gw ) {

		palette.draw( gw );

		// draw filled rectangles over the selected strokes
		gw.setCoordinateSystemToWorldSpaceUnits();
		for ( Stroke s : selectedStrokes ) {
			AlignedRectangle2D r = s.getBoundingRectangle();
			gw.setColor(1.0f,0.5f,0,0.2f); // transparent orange
			Vector2D diagonal = r.getDiagonal();
			gw.fillRect( r.getMin().x(), r.getMin().y(), diagonal.x(), diagonal.y() );
		}

		gw.setCoordinateSystemToPixels();

		// draw cursors
		for ( int i = 0; i < cursorContainer.getNumCursors(); ++i ) {
			MyCursor cursor = cursorContainer.getCursorByIndex( i );
			if ( cursor.type == MyCursor.TYPE_NOTHING )
				gw.setColor(0.5f,0,0,0.65f); // red (because this cursor is being ignored)
			else
				gw.setColor(0,0.5f,0.5f,0.65f); // cyan
			gw.fillCircle( cursor.getCurrentPosition().x()-10, cursor.getCurrentPosition().y()-10, 10 );

			if ( cursor.type == MyCursor.TYPE_INKING ) {
				// draw ink trail
				gw.setColor(0,0,0);
				gw.drawPolyline( cursor.getPositions() );
			}
			else if ( cursor.type == MyCursor.TYPE_SELECTION ) {
				if ( cursor.doesDragLookLikeLassoGesture() ) {
					// draw filled polygon
					gw.setColor(0,0,0,0.2f);
					gw.fillPolygon( cursor.getPositions() );
				}
				else {
					// draw polyline to indicate that a lasso could be started
					gw.setColor(0,0,0);
					gw.drawPolyline( cursor.getPositions() );

					// also draw selection rectangle
					gw.setColor(0,0,0,0.2f);
					Vector2D diagonal = Point2D.diff( cursor.getCurrentPosition(), cursor.getFirstPosition() );
					gw.fillRect( cursor.getFirstPosition().x(), cursor.getFirstPosition().y(), diagonal.x(), diagonal.y() );
				}
			}
		}
	}




	// returns true if a redraw is requested
	public boolean processMultitouchInputEvent(
		int id,
		float x, // in pixels
		float y, // in pixels
		int type,
		GraphicsWrapper gw,
		boolean doOtherUserContextsHaveCursors
	) {
		// Find the cursor that corresponds to the event id, if such a cursor already exists.
		// If no such cursor exists, the below index will be -1, and the reference to cursor will be null.
		int cursorIndex = cursorContainer.findIndexOfCursorById( id );
		MyCursor cursor = (cursorIndex==-1) ? null : cursorContainer.getCursorByIndex( cursorIndex );


		if ( cursor == null ) {

			if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
				// This should never happen, but if it does, just ignore the event.
				return false;
			}

			// The event does not correspond to any existing cursor.
			// In other words, this is a new finger touching the screen.
			// The event is probably of type TOUCH_EVENT_DOWN.
			// A new cursor will need to be created for the event.

			if ( palette.contains( x, y ) ) {
				// The event occurred inside the palette.

				if ( cursorContainer.getNumCursors() == 0 ) {
					// There are currently no cursors engaged for this user context.
					// In other words, this new finger is the only finger for the user context.
					// So, we allow the event for the new finger to activate a button in the palette.
					// We branch according to the button under the event.
					//
					int indexOfButton = palette.indexOfButtonContainingTheGivenPoint( x, y );
					if (
						indexOfButton == palette.movePalette_buttonIndex
					) {
						palette.buttons.get( indexOfButton ).isPressed = true;

						// Cause a new cursor to be created to keep track of this event id in the future
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_INTERACTING_WITH_WIDGET, indexOfButton );
					}
					else if (
						indexOfButton == palette.ink_buttonIndex
						|| indexOfButton == palette.select_buttonIndex
						|| indexOfButton == palette.manipulate_buttonIndex
						|| indexOfButton == palette.camera_buttonIndex
					) {
						// We transition to the mode corresponding to the button
						palette.buttons.get( palette.currentlyActiveModalButton ).isPressed = false;
						palette.currentlyActiveModalButton = indexOfButton;
						palette.buttons.get( indexOfButton ).isPressed = true;

						// Cause a new cursor to be created to keep track of this event id in the future
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_INTERACTING_WITH_WIDGET, indexOfButton );
					}
					else if (
						indexOfButton == palette.black_buttonIndex
						|| indexOfButton == palette.red_buttonIndex
						|| indexOfButton == palette.green_buttonIndex
					) {
						// We transition to the color corresponding to the button
						palette.buttons.get( palette.currentlyActiveColorButton ).isPressed = false;
						palette.currentlyActiveColorButton = indexOfButton;
						palette.buttons.get( indexOfButton ).isPressed = true;

						if ( indexOfButton == palette.black_buttonIndex ) {
							palette.current_red = 0;
							palette.current_green = 0;
							palette.current_blue = 0;
						}
						else if ( indexOfButton == palette.red_buttonIndex ) {
							palette.current_red = 1.0f;
							palette.current_green = 0;
							palette.current_blue = 0;
						}
						else if ( indexOfButton == palette.green_buttonIndex ) {
							palette.current_red = 0;
							palette.current_green = 1.0f;
							palette.current_blue = 0;
						}

						// Cause a new cursor to be created to keep track of this event id in the future
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_INTERACTING_WITH_WIDGET, indexOfButton );
					}
					else if ( indexOfButton == palette.horizFlip_buttonIndex ) {
						palette.buttons.get( indexOfButton ).isPressed = true;

						// Cause a new cursor to be created to keep track of this event id in the future
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_INTERACTING_WITH_WIDGET, indexOfButton );

						// Flip the selected strokes horizontally (around a vertical axis)
						for ( Stroke s : selectedStrokes ) {
							Point2D center = s.getBoundingRectangle().getCenter();
							for ( Point2D p : s.getPoints() ) {
								p.copy( center.x() - (p.x()-center.x()), p.y() );
							}
							s.markBoundingRectangleDirty();
						}
						drawing.markBoundingRectangleDirty();
					}
					else if ( indexOfButton == palette.frameAll_buttonIndex ) {
						palette.buttons.get( indexOfButton ).isPressed = true;

						// Cause a new cursor to be created to keep track of this event id in the future
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_INTERACTING_WITH_WIDGET, indexOfButton );

						// Frame the entire drawing
						gw.frame( drawing.getBoundingRectangle(), true );
					}
					else {
						// The event occurred on some part of the palette where there are no buttons.
						// We cause a new cursor to be created to keep track of this event id in the future.
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );

						// Prevent the cursor from doing anything in the future.
						cursor.setType( MyCursor.TYPE_NOTHING );
					}
				}
				else {
					// There is already at least one cursor.
					// In other words, there is already one or more other fingers being tracked in this user context
					// (possibly on a palette button, and/or over the drawing).
					// To keep things simple, we prevent this new finger from doing anything.

					// We create a new cursor ...
					cursorIndex = cursorContainer.updateCursorById( id, x, y );
					cursor = cursorContainer.getCursorByIndex( cursorIndex );

					// ... and prevent the cursor from doing anything in the future.
					cursor.setType( MyCursor.TYPE_NOTHING );
				}
			}
			else {
				// The event did not occur inside the palette.
				// This new finger may have been placed down to start
				// drawing a stroke, or start camera manipulation, etc.
				// We branch according to the current mode.
				//
				if ( palette.currentlyActiveModalButton == palette.ink_buttonIndex ) {
					// start drawing a stroke
					cursorIndex = cursorContainer.updateCursorById( id, x, y );
					cursor = cursorContainer.getCursorByIndex( cursorIndex );
					cursor.setType( MyCursor.TYPE_INKING );
				}
				else if ( palette.currentlyActiveModalButton == palette.select_buttonIndex ) {
					// The new finger should only start selecting
					// if there is not already another finger performing selection.
					if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_SELECTION ) == 0 ) {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_SELECTION );
					}
					else {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_NOTHING );
					}
				}
				else if ( palette.currentlyActiveModalButton == palette.manipulate_buttonIndex ) {
					// The new finger should only manipulate the selection
					// if there are not already 2 fingers manipulating the selection.
					if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_DIRECT_MANIPULATION ) < 2 ) {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_DIRECT_MANIPULATION );
					}
					else {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_NOTHING );
					}
				}
				else if ( palette.currentlyActiveModalButton == palette.camera_buttonIndex ) {
					// The new finger should only manipulate the camera
					// if there are not already 2 fingers manipulating the camera.
					if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_CAMERA_PAN_ZOOM ) < 2 ) {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_CAMERA_PAN_ZOOM );
					}
					else {
						cursorIndex = cursorContainer.updateCursorById( id, x, y );
						cursor = cursorContainer.getCursorByIndex( cursorIndex );
						cursor.setType( MyCursor.TYPE_NOTHING );
					}
				}

			}
		}
		else {
			// The event corresponds to an already existing cursor
			// (and the cursor was probably created during an earlier event of type TOUCH_EVENT_DOWN).
			// The current event is probably of type TOUCH_EVENT_MOVE or TOUCH_EVENT_UP.


			if ( type == MultitouchFramework.TOUCH_EVENT_MOVE ) {
				// The event is a move event, and corresponds to an existing cursor.
				// Is the location of the event different from the last reported location?
				Point2D newPosition = new Point2D( x, y );
				if ( cursor.getCurrentPosition().equals( newPosition ) ) {
					// The event's location is the same as last time.
					// Don't bother processing the event any further.
					return false; // do not request a redraw
				}
			}

			// We branch according to the type of cursor.
			//
			if ( cursor.type == MyCursor.TYPE_NOTHING ) {
				// Update the cursor with its new position.
				cursorContainer.updateCursorById( id, x, y );

				if ( type == MultitouchFramework.TOUCH_EVENT_UP )
					cursorContainer.removeCursorByIndex( cursorIndex );
			}
			else if ( cursor.type == MyCursor.TYPE_INTERACTING_WITH_WIDGET ) {
				if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
					// The user lifted their finger off of a palette button.
					cursorContainer.removeCursorByIndex( cursorIndex );

					if ( ! palette.buttons.get( cursor.indexOfButton ).isSticky ) {
						palette.buttons.get( cursor.indexOfButton ).isPressed = false;
					}
				}
				else {
					// Earlier, the user pressed down on a button in the palette,
					// and now they are dragging their finger over the button
					// (and possibly onto other buttons).
					// If this is the "move palette" button, we move the palette.
					if ( cursor.indexOfButton == palette.movePalette_buttonIndex ) {
						movePalette( x - cursor.getCurrentPosition().x(), y - cursor.getCurrentPosition().y() );
					}
					cursorIndex = cursorContainer.updateCursorById( id, x, y );
				}
			}
			else if ( cursor.type == MyCursor.TYPE_INKING ) {
				if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
					// up event
					cursorIndex = cursorContainer.updateCursorById( id, x, y );

					// Add the newly drawn stroke to the drawing
					Stroke newStroke = new Stroke();
					newStroke.setColor( palette.current_red, palette.current_green, palette.current_blue );
					for ( Point2D p : cursor.getPositions() ) {
						newStroke.addPoint( gw.convertPixelsToWorldSpaceUnits( p ) );
					}
					drawing.addStroke( newStroke );

					cursorContainer.removeCursorByIndex( cursorIndex );
				}
				else {
					// drag event; just update the cursor with the new position
					cursorIndex = cursorContainer.updateCursorById( id, x, y );
				}
			}
			else if ( cursor.type == MyCursor.TYPE_CAMERA_PAN_ZOOM ) {
				if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
					// up event
					cursorContainer.removeCursorByIndex( cursorIndex );
				}
				else {
					// drag event
					cursorIndex = cursorContainer.updateCursorById( id, x, y );

					if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_CAMERA_PAN_ZOOM ) == 2 ) {
						MyCursor cursor0 = cursorContainer.getCursorByType( MyCursor.TYPE_CAMERA_PAN_ZOOM, 0 );
						MyCursor cursor1 = cursorContainer.getCursorByType( MyCursor.TYPE_CAMERA_PAN_ZOOM, 1 );
						gw.panAndZoomBasedOnDisplacementOfTwoPoints(
							id==cursor0.id ? cursor0.getPreviousPosition() : cursor0.getCurrentPosition(),
							id==cursor1.id ? cursor1.getPreviousPosition() : cursor1.getCurrentPosition(),
							cursor0.getCurrentPosition(),
							cursor1.getCurrentPosition()
						);
					}
					else if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_CAMERA_PAN_ZOOM ) == 1 ) {
						gw.pan(
							cursor.getCurrentPosition().x() - cursor.getPreviousPosition().x(),
							cursor.getCurrentPosition().y() - cursor.getPreviousPosition().y()
						);
					}
				}
			}
			else if ( cursor.type == MyCursor.TYPE_SELECTION ) {
				if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
					// up event
					cursorIndex = cursorContainer.updateCursorById( id, x, y );

					// Update the selection
					if ( cursor.doesDragLookLikeLassoGesture() ) {
						// complete a lasso selection

						// Need to transform the positions of the cursor from pixels to world space coordinates.
						// We will store the world space coordinates in the following data structure.
						ArrayList< Point2D > lassoPolygonPoints = new ArrayList< Point2D >();
						for ( Point2D p : cursor.getPositions() ) {
							lassoPolygonPoints.add( gw.convertPixelsToWorldSpaceUnits( p ) );
						}

						selectedStrokes.clear();
						for ( Stroke s : drawing.strokes ) {
							if ( s.isContainedInLassoPolygon( lassoPolygonPoints ) )
								selectedStrokes.add( s );
						}
					}
					else {
						// complete a rectangle selection

						AlignedRectangle2D selectedRectangle = new AlignedRectangle2D(
							gw.convertPixelsToWorldSpaceUnits( cursor.getFirstPosition() ),
							gw.convertPixelsToWorldSpaceUnits( cursor.getCurrentPosition() )
						);

						selectedStrokes.clear();
						for ( Stroke s : drawing.strokes ) {
							if ( s.isContainedInRectangle( selectedRectangle ) )
								selectedStrokes.add( s );
						}
					}

					cursorContainer.removeCursorByIndex( cursorIndex );
				}
				else {
					// drag event; just update the cursor with the new position
					cursorIndex = cursorContainer.updateCursorById( id, x, y );
				}
			}
			else if ( cursor.type == MyCursor.TYPE_DIRECT_MANIPULATION ) {
				if ( type == MultitouchFramework.TOUCH_EVENT_UP ) {
					// up event
					cursorContainer.removeCursorByIndex( cursorIndex );
				}
				else {
					// drag event
					cursorIndex = cursorContainer.updateCursorById( id, x, y );

					if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_DIRECT_MANIPULATION ) == 2 ) {
						MyCursor cursor0 = cursorContainer.getCursorByType( MyCursor.TYPE_DIRECT_MANIPULATION, 0 );
						MyCursor cursor1 = cursorContainer.getCursorByType( MyCursor.TYPE_DIRECT_MANIPULATION, 1 );

						// convert cursor positions to world space
						Point2D cursor0_currentPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor0.getCurrentPosition() );
						Point2D cursor1_currentPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor1.getCurrentPosition() );
						Point2D cursor0_previousPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor0.getPreviousPosition() );
						Point2D cursor1_previousPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor1.getPreviousPosition() );

						for ( Stroke s : selectedStrokes ) {
							Point2DUtil.transformPointsBasedOnDisplacementOfTwoPoints(
								s.getPoints(),
								id==cursor0.id ? cursor0_previousPosition_worldSpace : cursor0_currentPosition_worldSpace,
								id==cursor1.id ? cursor1_previousPosition_worldSpace : cursor1_currentPosition_worldSpace,
								cursor0_currentPosition_worldSpace,
								cursor1_currentPosition_worldSpace
							);
							s.markBoundingRectangleDirty();
						}
						drawing.markBoundingRectangleDirty();
					}
					else if ( cursorContainer.getNumCursorsOfGivenType( MyCursor.TYPE_DIRECT_MANIPULATION ) == 1 ) {
						// convert cursor positions to world space
						Point2D cursor_currentPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor.getCurrentPosition() );
						Point2D cursor_previousPosition_worldSpace = gw.convertPixelsToWorldSpaceUnits( cursor.getPreviousPosition() );

						// compute translation vector
						Vector2D translationVector = Point2D.diff( cursor_currentPosition_worldSpace, cursor_previousPosition_worldSpace );

						// apply the translation to the selected strokes
						for ( Stroke s : selectedStrokes ) {
							for ( Point2D p : s.getPoints() ) {
								p.copy( Point2D.sum(p,translationVector) );
							}
							s.markBoundingRectangleDirty();
						}
						drawing.markBoundingRectangleDirty();
					}
				}
			}
		}

		return true; // request a redraw

	}
}


public class SimpleWhiteboard implements Runnable, ActionListener {

	public MultitouchFramework multitouchFramework = null;
	public GraphicsWrapper gw = null;
	JMenuItem testMenuItem1;
	JMenuItem testMenuItem2;
	JButton frameAllButton;
	JButton testButton1;
	JButton testButton2;

	Thread thread = null;
	boolean threadSuspended;

	int mouse_x, mouse_y;

	Drawing drawing = new Drawing();

	UserContext [] userContexts = null;

	public SimpleWhiteboard( MultitouchFramework mf, GraphicsWrapper gw ) {
		multitouchFramework = mf;
		this.gw = gw;
		multitouchFramework.setPreferredWindowSize(Constant.INITIAL_WINDOW_WIDTH,Constant.INITIAL_WINDOW_HEIGHT);

		userContexts = new UserContext[ Constant.NUM_USERS ];
		for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
			userContexts[j] = new UserContext( drawing );
		}
		// initialize the positions of the palettes
		if ( Constant.NUM_USERS == 1 ) {
			userContexts[0].setPositionOfCenterOfPalette( Constant.INITIAL_WINDOW_WIDTH / 2, Constant.INITIAL_WINDOW_HEIGHT / 2 );
		}
		else {
			// Compute a circular layout of the palettes
			float radius = Math.min( Constant.INITIAL_WINDOW_WIDTH, Constant.INITIAL_WINDOW_HEIGHT )/4;
			for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
				float angleInRadians = (float)( 2 * Math.PI * j / Constant.NUM_USERS );
				userContexts[j].setPositionOfCenterOfPalette(
					Constant.INITIAL_WINDOW_WIDTH / 2 + (float)(radius*Math.cos(angleInRadians)),
					Constant.INITIAL_WINDOW_HEIGHT / 2 + (float)(radius*Math.sin(angleInRadians))
				);
			}
		}

		gw.setFontHeight( Constant.TEXT_HEIGHT );

		gw.frame( new AlignedRectangle2D( new Point2D(-100,-100), new Point2D(100,100) ), true );
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if ( source == testMenuItem1 ) {
			System.out.println("testMenuItem1 has been selected");
		}
		else if ( source == testMenuItem2 ) {
			System.out.println("testMenuItem2 has been selected");
		}
		else if ( source == frameAllButton ) {
			gw.frame( drawing.getBoundingRectangle(), true );
			multitouchFramework.requestRedraw();
		}
		else if ( source == testButton1 ) {
			System.out.println("testButton1 has been selected");
		}
		else if ( source == testButton2 ) {
			System.out.println("testButton2 has been selected");
		}
	}

	// Called by the framework when creating widgets.
	public JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
			JMenu menu = new JMenu("File");
				testMenuItem1 = new JMenuItem("Test 1");
				testMenuItem1.addActionListener(this);
				menu.add(testMenuItem1);
				testMenuItem2 = new JMenuItem("Test 2");
				testMenuItem2.addActionListener(this);
				menu.add(testMenuItem2);
			menuBar.add(menu);
		return menuBar;
	}

	// Called by the framework when creating widgets.
	public JPanel createPanelOfWidgets() {
		JPanel panelOfWidgets = new JPanel();
		panelOfWidgets.setLayout( new BoxLayout( panelOfWidgets, BoxLayout.Y_AXIS ) );
		frameAllButton = new JButton("Frame All");
		frameAllButton.addActionListener(this);
		panelOfWidgets.add( frameAllButton );
		testButton1 = new JButton("Test number 1 ...");
		testButton1.addActionListener(this);
		panelOfWidgets.add( testButton1 );
		testButton2 = new JButton("Test number 2 ...");
		testButton2.addActionListener(this);
		panelOfWidgets.add( testButton2 );
		return panelOfWidgets;
	}

	// Called by the framework at startup time.
	public void startBackgroundWork() {
		if ( thread == null ) {
			thread = new Thread( this );
			threadSuspended = false;
			thread.start();
		}
		else {
			if ( threadSuspended ) {
				threadSuspended = false;
				synchronized( this ) {
					notify();
				}
			}
		}
	}
	public void stopBackgroundWork() {
		threadSuspended = true;
	}
	public void run() {
		try {
			int sleepIntervalInMilliseconds = 1000;
			while (true) {

				// Here's where the thread does some work
				synchronized( this ) {
					// System.out.println("some background work");
					// ...
				}
				// multitouchFramework.requestRedraw();

				// Now the thread checks to see if it should suspend itself
				if ( threadSuspended ) {
					synchronized( this ) {
						while ( threadSuspended ) {
							wait();
						}
					}
				}
				thread.sleep( sleepIntervalInMilliseconds );  // interval given in milliseconds
			}
		}
		catch (InterruptedException e) { }
	}

	public synchronized void draw() {
		gw.clear(1,1,1);
		gw.setColor(0,0,0);
		gw.setupForDrawing();

		gw.setCoordinateSystemToWorldSpaceUnits();
		gw.enableAlphaBlending();

		drawing.draw( gw );

		gw.setCoordinateSystemToPixels();

		for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
			userContexts[j].draw(gw);
		}


		// Draw some text to indicate the number of fingers touching the user interface.
		// This is useful for debugging.
		int totalNumCursors = 0;
		String s = "[";
		for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
			totalNumCursors += userContexts[j].getNumCursors();
			s += (j==0?"":"+") + userContexts[j].getNumCursors();
		}
		s += " contacts]";
		if ( totalNumCursors > 0 ) {
			gw.setColor(0,0,0);
			gw.setFontHeight( Constant.TEXT_HEIGHT );
			gw.drawString(
				Constant.TEXT_HEIGHT,
				2 * Constant.TEXT_HEIGHT,
				s
			);
		}


	}

	public synchronized void keyPressed( KeyEvent e ) {
		System.out.println("Key '" + e.getKeyChar() + "' pressed!");
	}
	public synchronized void keyReleased( KeyEvent e ) {
	}
	public synchronized void keyTyped( KeyEvent e ) {
		System.out.println("Key '" + e.getKeyChar() + "' typed!");
	}
	public synchronized void mouseEntered( MouseEvent e ) {
	}
	public synchronized void mouseExited( MouseEvent e ) {
	}
	public synchronized void mouseClicked( MouseEvent e ) {
	}


	public synchronized void mousePressed(MouseEvent e) {
		mouse_x = e.getX();
		mouse_y = e.getY();

		// multitouchFramework.requestRedraw();
	}

	public synchronized void mouseReleased(MouseEvent e) {
		mouse_x = e.getX();
		mouse_y = e.getY();

		// multitouchFramework.requestRedraw();
	}


	public synchronized void mouseDragged(MouseEvent e) {

		mouse_x = e.getX();
		mouse_y = e.getY();

		// multitouchFramework.requestRedraw();
	}


	public synchronized void mouseMoved(MouseEvent e) {
		mouse_x = e.getX();
		mouse_y = e.getY();

		// multitouchFramework.requestRedraw();
	}

	// Returns the index of the user context that is most appropriate for handling this event.
	private int findIndexOfUserContextForMultitouchInputEvent( int id, float x, float y ) {

		// If there is a user context that already has a cursor with the given id,
		// then we return the index of that user context.
		for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
			if ( userContexts[j].hasCursorID( id ) )
				return j;
		}

		// None of the user contexts have a cursor with the given id.
		// So, we find the user context whose palette is closest to the given event location.
		// (Later, that user context will create a new cursor with the given id,
		// so it will continue to process future events for the same cursor.)
		int indexOfClosestUserContext = 0;
		float distanceOfClosestUserContext = userContexts[0].distanceToPalette( x, y );
		for ( int j = 1; j < Constant.NUM_USERS; ++j ) {
			float candidateDistance = userContexts[j].distanceToPalette( x, y );
			if ( candidateDistance < distanceOfClosestUserContext ) {
				indexOfClosestUserContext = j;
				distanceOfClosestUserContext = candidateDistance;
			}
		}
		return indexOfClosestUserContext;
	}

	public synchronized void processMultitouchInputEvent( int id, float x, float y, int type ) {
		//System.out.println("event: "+id+", "+x+", "+y+", "+type);

		int indexOfUserContext = findIndexOfUserContextForMultitouchInputEvent( id, x, y );

		boolean doOtherUserContextsHaveCursors = false;
		for ( int j = 0; j < Constant.NUM_USERS; ++j ) {
			if ( j != indexOfUserContext && userContexts[j].hasCursors() ) {
				doOtherUserContextsHaveCursors = true;
				break;
			}
		}

		boolean redrawRequested = userContexts[indexOfUserContext].processMultitouchInputEvent( id, x, y, type, gw, doOtherUserContextsHaveCursors );
		if ( redrawRequested )
			multitouchFramework.requestRedraw();
	}
}


