import java.util.ArrayList;


public class Memento {
	private static Memento instance;
	
	ArrayList<ArrayList< Stroke >> listStrokes;
	int presentIndex;
	
	public Memento() {
		presentIndex = 0;
		listStrokes = new ArrayList<ArrayList<Stroke>>();
		saveStrokes(new ArrayList<Stroke>());
	}

	public static Memento getInstance() {
		if (instance==null) {
			instance = new Memento();
		}
		return instance;
	}
	
	public ArrayList< Stroke > undo() {
		if (presentIndex > 0 && listStrokes.size() > 1) {
			presentIndex--;
		}
	//	System.out.println("Strokes nb: "+listStrokes.get(presentIndex).size());
		return listStrokes.get(presentIndex);
	}
	
	public ArrayList< Stroke > redo() {
		if (presentIndex < listStrokes.size()-1) {
			presentIndex++;
		}
		return listStrokes.get(presentIndex);
	}
	
	public void saveStrokes(ArrayList<Stroke> strokes) {
	//	System.out.println("Save1 "+presentIndex+" "+listStrokes.size());
		while (presentIndex < listStrokes.size()-1) {
			listStrokes.remove(presentIndex+1);
		}
	//	System.out.println("Strokes nb: "+strokes.size());
		
		listStrokes.add(strokes);
		presentIndex = listStrokes.size()-1;
		
		int i = 0;
		for (ArrayList<Stroke> strokeList : listStrokes) {
			System.out.println("Stroke "+i+": "+strokeList.size());
			i++;
		}
	//	System.out.println("Save2 "+presentIndex+" "+listStrokes.size());
	}
	
	/*
	public void saveSimpleWhiteboard(SimpleWhiteboard gw) {
		System.out.println("Save1 "+presentIndex+" "+gwList.size());
		if (presentIndex < gwList.size()-1) {
			while (presentIndex+1 < gwList.size()) {
				gwList.remove(presentIndex+1);
			}
		}
		gwList.add(gw);
		presentIndex++;
		
		System.out.println("Save2 "+presentIndex+" "+gwList.size());
	}*/
}
