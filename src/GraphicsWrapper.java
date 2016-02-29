

import javax.media.opengl.GL;
//import javax.media.opengl.GLCanvas;
//import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLAutoDrawable;
// import javax.media.opengl.GLDrawableFactory;
//import javax.media.opengl.GLEventListener;
import com.sun.opengl.util.GLUT;


import java.lang.Math;
import java.util.ArrayList;
import java.awt.Color;


class GraphicsWrapper {


	private int windowWidthInPixels = 10; // must be initialized to something positive
	private int windowHeightInPixels = 10; // must be initialized to something positive

	// The client may either call frame() or resize() first,
	// and we must initialize ourself differently depending on the case.
	private boolean hasFrameOrResizeBeenCalledBefore = false;

	public int getWidth() { return windowWidthInPixels; }
	public int getHeight() { return windowHeightInPixels; }




	private GL gl = null;
	private GLUT glut = null;

	public void set( GLAutoDrawable drawable ) { this.gl = drawable.getGL(); if (glut==null) glut=new GLUT(); }


	private int fontHeight = 14;
	public void setFontHeight( int h ) {
		fontHeight = h;
	}
	public int getFontHeight() {
		return fontHeight;
	}




	// From the manual page for glutStrokeCharacter():
	// The Mono Roman font supported by GLUT has characters that are
	// 104.76 units wide, up to 119.05 units high, and descenders can
	// go as low as 33.33 units.
	//
	// The "G_" prefix is because it is specific to GLUT.
	// We don't use the prefix "GLUT_", because that's
	// reserved for stuff that GLUT defines itself.
	private static final float G_FONT_ASCENT = 119.05f;
	private static final float G_FONT_DESCENT = 33.33f;
	private static final float G_CHAR_WIDTH = 104.76f;
	// This is the recommended spacing,
	// it's chosen (somewhat arbitrarily) to match that in a bitmap font that is 18 pixels high,
	// and uses 1 row of pixels in every character bitmap for vertical spacing.
	private static final float G_FONT_VERTICAL_SPACE = (1.0f/17)*(G_FONT_ASCENT+G_FONT_DESCENT);





	private float offsetXInPixels = 0;
	private float offsetYInPixels = 0;
	private float scaleFactorInWorldSpaceUnitsPerPixel = 1.0f; // greater if user is more zoomed out

	public float convertPixelsToWorldSpaceUnitsX( float XInPixels ) { return ( XInPixels - offsetXInPixels )*scaleFactorInWorldSpaceUnitsPerPixel; }
	public float convertPixelsToWorldSpaceUnitsY( float YInPixels ) { return ( YInPixels - offsetYInPixels )*scaleFactorInWorldSpaceUnitsPerPixel; }
	public Point2D convertPixelsToWorldSpaceUnits( Point2D p ) { return new Point2D(convertPixelsToWorldSpaceUnitsX(p.x()),convertPixelsToWorldSpaceUnitsY(p.y())); }

	public int convertWorldSpaceUnitsToPixelsX( float x ) { return Math.round( x / scaleFactorInWorldSpaceUnitsPerPixel + offsetXInPixels ); }
	public int convertWorldSpaceUnitsToPixelsY( float y ) { return Math.round( y / scaleFactorInWorldSpaceUnitsPerPixel + offsetYInPixels ); }
	public Point2D convertWorldSpaceUnitsToPixels( Point2D p ) { return new Point2D(convertWorldSpaceUnitsToPixelsX(p.x()),convertWorldSpaceUnitsToPixelsY(p.y())); }

	public float getScaleFactorInWorldSpaceUnitsPerPixel() { return scaleFactorInWorldSpaceUnitsPerPixel; }

	public void pan( float dx, float dy ) {
		offsetXInPixels += dx;
		offsetYInPixels += dy;
	}
	public void zoomIn(
		float zoomFactor, // greater than 1 to zoom in, between 0 and 1 to zoom out
		float centerXInPixels,
		float centerYInPixels
	) {
		scaleFactorInWorldSpaceUnitsPerPixel /= zoomFactor;
		offsetXInPixels = centerXInPixels - (centerXInPixels - offsetXInPixels) * zoomFactor;
		offsetYInPixels = centerYInPixels - (centerYInPixels - offsetYInPixels) * zoomFactor;
	}
	public void zoomIn(
		float zoomFactor // greater than 1 to zoom in, between 0 and 1 to zoom out
	) {
		zoomIn( zoomFactor, windowWidthInPixels * 0.5f, windowHeightInPixels * 0.5f );
	}

	// This can be used to implement bimanual (2-handed) camera control,
	// or 2-finger camera control, as in a "pinch" gesture
	public void panAndZoomBasedOnDisplacementOfTwoPoints(
		// these are assumed to be in pixel coordinates
		Point2D A_old, Point2D B_old,
		Point2D A_new, Point2D B_new
	) {
		// Compute midpoints of each pair of points
		Point2D M1 = Point2D.average( A_old, B_old );
		Point2D M2 = Point2D.average( A_new, B_new );

		// This is the translation that the world should appear to undergo.
		Vector2D translation = Point2D.diff( M2, M1 );

		// Compute a vector associated with each pair of points.
		Vector2D v1 = Point2D.diff( A_old, B_old );
		Vector2D v2 = Point2D.diff( A_new, B_new );

		float v1_length = v1.length();
		float v2_length = v2.length();
		float scaleFactor = 1;
		if ( v1_length > 0 && v2_length > 0 )
			scaleFactor = v2_length / v1_length;
		pan( translation.x(), translation.y() );
		zoomIn( scaleFactor, M2.x(), M2.y() );
	}

	public void frame(
		AlignedRectangle2D rect,
		boolean expand // true if caller wants a margin of whitespace added around the rect
	) {
		hasFrameOrResizeBeenCalledBefore = true;
		assert windowWidthInPixels > 0 && windowHeightInPixels > 0;

		if ( rect.isEmpty() || rect.getDiagonal().x() == 0 || rect.getDiagonal().y() == 0 ) {
			return;
		}
		if ( expand ) {
			float diagonal = rect.getDiagonal().length() / 20;
			Vector2D v = new Vector2D( diagonal, diagonal );
			rect = new AlignedRectangle2D( Point2D.diff(rect.getMin(),v), Point2D.sum(rect.getMax(),v) );
		}
		if ( rect.getDiagonal().x() / rect.getDiagonal().y() >= windowWidthInPixels / (float)windowHeightInPixels ) {
			offsetXInPixels = - rect.getMin().x() * windowWidthInPixels / rect.getDiagonal().x();
			scaleFactorInWorldSpaceUnitsPerPixel = rect.getDiagonal().x() / windowWidthInPixels;
			offsetYInPixels = windowHeightInPixels/2 - rect.getCenter().y() / scaleFactorInWorldSpaceUnitsPerPixel;
		}
		else {
			offsetYInPixels = - rect.getMin().y() * windowHeightInPixels / rect.getDiagonal().y();
			scaleFactorInWorldSpaceUnitsPerPixel = rect.getDiagonal().y() / windowHeightInPixels;
			offsetXInPixels = windowWidthInPixels/2 - rect.getCenter().x() / scaleFactorInWorldSpaceUnitsPerPixel;
		}
	}


	public void resize( int w, int h ) {
		if ( ! hasFrameOrResizeBeenCalledBefore ) {
			windowWidthInPixels = w;
			windowHeightInPixels = h;
			gl.glViewport( 0, 0, w, h );
			hasFrameOrResizeBeenCalledBefore = true;
			return;
		}

		Point2D oldCenter = convertPixelsToWorldSpaceUnits( new Point2D(
			windowWidthInPixels * 0.5f, windowHeightInPixels * 0.5f
		) );
		float radius = Math.min( windowWidthInPixels, windowHeightInPixels ) * 0.5f * scaleFactorInWorldSpaceUnitsPerPixel;


		windowWidthInPixels = w;
		windowHeightInPixels = h;
		gl.glViewport( 0, 0, w, h );

		if ( radius > 0 ) {
			frame(
				new AlignedRectangle2D(
					new Point2D( oldCenter.x() - radius, oldCenter.y() - radius ),
					new Point2D( oldCenter.x() + radius, oldCenter.y() + radius )
				),
				false
			);
		}
	}

	public void setCoordinateSystemToPixels() {
		gl.glMatrixMode( GL.GL_PROJECTION );
		gl.glLoadIdentity();
		gl.glScalef( 1, -1, 1 ); // flips y axis from y+ down to y+ up
		gl.glTranslatef( -1, -1, 0 ); // transforms [0,2]x[0,2] to [-1,1]x[-1,1]
		gl.glScalef( // transforms [0,w]x[0,h] to [0,2]x[0,2]
			2.0f/windowWidthInPixels,
			2.0f/windowHeightInPixels,
			1
		);
	}

	public void setCoordinateSystemToWorldSpaceUnits() {
		gl.glMatrixMode( GL.GL_PROJECTION );
		gl.glLoadIdentity();
		gl.glScalef( 1, -1, 1 ); // flips y axis from y+ down to y+ up
		gl.glTranslatef( -1, -1, 0 ); // transforms [0,2]x[0,2] to [-1,1]x[-1,1]
		gl.glScalef( // transforms [0,w*s]x[0,h*s] to [0,2]x[0,2]
			2.0f/(scaleFactorInWorldSpaceUnitsPerPixel*windowWidthInPixels),
			2.0f/(scaleFactorInWorldSpaceUnitsPerPixel*windowHeightInPixels),
			1
		);
		gl.glTranslatef( // transforms [- offset_x * s, ( w - offset_x ) * s]x[- offset_y * s, ( h - offset_y ) * s] to [0,w*s]x[0,h*s]
			offsetXInPixels * scaleFactorInWorldSpaceUnitsPerPixel,
			offsetYInPixels * scaleFactorInWorldSpaceUnitsPerPixel,
			0
		);
	}

	public void clear( float r, float g, float b ) {
		gl.glClearColor( r, g, b, 0 );
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
	}

	public void setupForDrawing() {
		gl.glMatrixMode( GL.GL_MODELVIEW );
		gl.glLoadIdentity();
		//gl.glDepthFunc( GL.GL_LEQUAL );
		gl.glDisable( GL.GL_DEPTH_TEST );
		gl.glDisable( GL.GL_CULL_FACE );
		gl.glDisable( GL.GL_LIGHTING );
		gl.glShadeModel( GL.GL_FLAT );
	}

	public void enableAlphaBlending() {
		gl.glEnable( GL.GL_LINE_SMOOTH );
		gl.glBlendFunc( GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA );
		gl.glEnable( GL.GL_BLEND );
	}

	public void disableAlphaBlending() {
		gl.glDisable( GL.GL_LINE_SMOOTH );
		gl.glDisable( GL.GL_BLEND );
	}

	public void setColor( float r, float g, float b ) {
		gl.glColor3f( r, g, b );
	}

	public void setColor( float r, float g, float b, float alpha ) {
		gl.glColor4f( r, g, b, alpha );
	}

	public void setColor( Color c ) {
		setColor( c.getRed()/255.0f, c.getGreen()/255.0f, c.getBlue()/255.0f, c.getAlpha()/255.0f );
	}

	public void setColor( Color c, float alpha ) {
		setColor( c.getRed()/255.0f, c.getGreen()/255.0f, c.getBlue()/255.0f, alpha );
	}

	public void setLineWidth( float width ) {
		gl.glLineWidth( width );
	}

	public void drawLine( float x1, float y1, float x2, float y2 ) {
		gl.glBegin( GL.GL_LINES );
			gl.glVertex3f( x1, y1, 0 );
			gl.glVertex3f( x2, y2, 0 );
		gl.glEnd();
		gl.glBegin( GL.GL_POINTS );
			gl.glVertex2f( x2, y2 );
		gl.glEnd();
	}

	public void drawPolyline( ArrayList< Point2D > points, boolean isClosed, boolean isFilled ) {
		if ( points.size() <= 1 )
			return;
		if ( isFilled ) gl.glBegin( GL.GL_POLYGON );
		else gl.glBegin( isClosed ? GL.GL_LINE_LOOP : GL.GL_LINE_STRIP );

			for ( int i = 0; i < points.size(); ++i ) {
				Point2D p = points.get(i);
				gl.glVertex2fv( p.get(), 0 );
			}
		gl.glEnd();
	}

	public void drawPolyline( ArrayList< Point2D > points ) {
		drawPolyline( points, false, false );
	}
	public void drawPolygon( ArrayList< Point2D > points ) {
		drawPolyline( points, true, false );
	}
	public void fillPolygon( ArrayList< Point2D > points ) {
		drawPolyline( points, true, true );
	}

	public void drawRect( float x, float y, float w, float h, boolean isFilled ) {
		if ( isFilled ) fillRect( x, y, w, h );
		else drawRect( x, y, w, h );
	}

	public void drawRect( float x, float y, float w, float h ) {
		gl.glBegin( GL.GL_LINE_LOOP );
			gl.glVertex2f( x, y );
			gl.glVertex2f( x, y+h );
			gl.glVertex2f( x+w, y+h );
			gl.glVertex2f( x+w, y );
		gl.glEnd();
	}

	public void fillRect( float x, float y, float w, float h ) {
		gl.glBegin( GL.GL_QUADS );
			gl.glVertex2f( x, y );
			gl.glVertex2f( x, y+h );
			gl.glVertex2f( x+w, y+h );
			gl.glVertex2f( x+w, y );
		gl.glEnd();
	}

	public void drawCircle( float x, float y, float radius, boolean isFilled ) {
		x += radius;
		y += radius;
		if ( isFilled ) {
			gl.glBegin( GL.GL_TRIANGLE_FAN );
			gl.glVertex2f( x, y );
		}
		else gl.glBegin( GL.GL_LINE_LOOP );

			int numSides = (int)( 2 * Math.PI * radius + 1 );
			float deltaAngle = 2 * (float)Math.PI / numSides;

			for ( int i = 0; i <= numSides; ++i ) {
				float angle = i * deltaAngle;
				gl.glVertex2f( x+radius*(float)Math.cos(angle), y+radius*(float)Math.sin(angle) );
			}
		gl.glEnd();
	}

	public void drawCircle( float x, float y, float radius ) {
		drawCircle( x, y, radius, false );
	}

	public void fillCircle( float x, float y, float radius ) {
		drawCircle( x, y, radius, true );
	}

	public void drawCenteredCircle( float x, float y, float radius, boolean isFilled ) {
		x -= radius;
		y -= radius;
		drawCircle( x, y, radius, isFilled );
	}

	public void drawArc(
		float center_x, // increases right
		float center_y, // increases down
		float radius,
		float startAngle, // in radians; zero for right, increasing counterclockwise
		float angleExtent, // in radians; positive for counterclockwise
		boolean isFilled
	) {
		if ( isFilled ) {
			if ( angleExtent < 0 )
				gl.glFrontFace( GL.GL_CW );
			gl.glBegin( GL.GL_TRIANGLE_FAN );
			gl.glVertex2f( center_x, center_y );
		}
		else gl.glBegin( GL.GL_LINE_STRIP );

			int numSides = (int)( Math.abs(angleExtent) * radius + 1 );
			float deltaAngle = angleExtent / numSides;

			for ( int i = 0; i <= numSides; ++i ) {
				float angle = -( startAngle + i * deltaAngle );
				gl.glVertex2f( center_x+radius*(float)Math.cos(angle), center_y+radius*(float)Math.sin(angle) );
			}
		gl.glEnd();

		if ( isFilled && angleExtent < 0 )
			gl.glFrontFace( GL.GL_CCW );
	}

	public void drawArc(
		float center_x, float center_y, float radius,
		float startAngle, // in radians
		float angleExtent // in radians
	) {
		drawArc( center_x, center_y, radius, startAngle, angleExtent, false );
	}

	public void fillArc(
		float center_x, float center_y, float radius,
		float startAngle, // in radians
		float angleExtent // in radians
	) {
		drawArc( center_x, center_y, radius, startAngle, angleExtent, true );
	}



	// returns the width of a string
	public float stringWidth( String s ) {
		if ( s == null || s.length() == 0 ) return 0;
		float h, w_over_h;

		h = G_FONT_ASCENT /* + G_FONT_DESCENT + G_FONT_VERTICAL_SPACE */;
		w_over_h = G_CHAR_WIDTH / h;

		return fontHeight * s.length() * w_over_h;
	}


	public void drawString(
		float x, float y,      // lower left corner of the string
		String s           // the string
	) {
		if ( s == null || s.length() == 0 ) return;

		float ascent = fontHeight * G_FONT_ASCENT
			/ ( G_FONT_ASCENT /* + G_FONT_DESCENT + G_FONT_VERTICAL_SPACE */ );
		y += fontHeight - ascent;

		gl.glPushMatrix();
			gl.glTranslatef( x, y, 0 );
			gl.glScalef( 1, -1, 1 ); // to flip the y axis

			// We scale the text to make its height that desired by the caller.
			float sf = ascent / G_FONT_ASCENT; // scale factor
			gl.glScalef( sf, sf, 1 );
			for ( int j = 0; j < s.length(); ++j )
				glut.glutStrokeCharacter( GLUT.STROKE_MONO_ROMAN, s.charAt(j) );
		gl.glPopMatrix();
	}


}


