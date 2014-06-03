package fieldbox.boxes;

import field.graphics.FLine;
import field.graphics.FLinesAndJavaShapes;
import field.graphics.Window;
import field.linalg.Vec2;
import field.utility.Cached;
import field.utility.Dict;
import field.utility.Rect;

import java.awt.*;
import java.awt.geom.Area;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fundamental interaction support for FLines when placed on the canvas.
 * <p>
 * FLines can respond to mouse events --- OnMouseEnter and OnMouseExit. Add FLines that support interaction to the "interactiveLines" property
 * (signature Map<String, Function<Box, FLine>>). Add implementations of Mouse.OnMouseEnter,Mouse.OnMouseExit & Mouse.OnMouseDown to FLine.attributes (the shape of the line governs the sense of "enter" and "exit"). See MarkingMenus
 * for an example of the use of this class.
 */
public class FLineInteraction extends Box implements Drawing.Drawer, Mouse.OnMouseDown, Mouse.OnMouseMove {

	static public final Dict.Prop<FLineInteraction> interaction = new Dict.Prop<>("interaction").type().toCannon().doc("the FLineInteraction Plugin");
	static public final Dict.Prop<Cached<FLine, Object, Area>> projectedArea = new Dict.Prop<>("_projectedArea");
	static public final Dict.Prop<Map<String, Function<Box, FLine>>> interactiveLines = new Dict.Prop<>("interactiveLines").type().toCannon().doc("add lines to this property to make them interactive. OnMouseExit and OnMouseEnter attributes will be called appropriately");

	public FLineInteraction(Box root) {
		properties.putToList(Drawing.drawers, this);
		properties.put(interaction, this);
		properties.putToList(Mouse.onMouseDown, this);
		properties.putToList(Mouse.onMouseMove, this);
	}

	Set<FLine> all = null;

	@Override
	public void draw(Drawing context) {
		all = new LinkedHashSet<>();
		this.breadthFirst(this.both()).forEach(x -> {
			Rect r = x.properties.get(frame);

			Map<String, Function<Box, FLine>> drawing = x.properties.get(interactiveLines);
			if (drawing == null) return;

			Iterator<Function<Box, FLine>> it = drawing.values().iterator();
			while (it.hasNext()) {
				Function<Box, FLine> f = it.next();
				FLine fl = f.apply(x);
				if (fl == null) it.remove();
				else all.add(fl);
			}
		});
		for (FLine f : all)
			f.attributes.computeIfAbsent(projectedArea, (k) -> new Cached<FLine, Object, Area>((fline, previously) -> projectFLineToArea(fline), (fline) -> new Object[]{fline, fline
				    .getModCount()}));
	}

	public Area projectFLineToArea(FLine fline) {
		Shape s = FLinesAndJavaShapes.flineToJavaShape(fline);
		return new Area(s);
	}

	public Vec2 convertCoordinateSystem(Vec2 event) {
		Optional<Drawing> drawing = this.find(Drawing.drawing, both()).findFirst();
		return drawing.map(x -> x.windowSystemToDrawingSystem(event))
			    .orElseThrow(() -> new IllegalArgumentException(" cant mouse around something without drawing support (to provide coordinate system)"));
	}

	public boolean intersects(FLine f, Vec2 position) {
		Cached<FLine, Object, Area> area = f.attributes
			    .computeIfAbsent(projectedArea, (k) -> new Cached<FLine, Object, Area>((fline, previously) -> projectFLineToArea(fline), (fline) -> new Object[]{fline, fline
					.getModCount()}));
		return area.apply(f).contains(position.x, position.y);
	}


	@Override
	public Mouse.Dragger onMouseDown(Window.Event<Window.MouseState> e, int button) {

		// if we haven't been drawn, then we can't interact

		if (all == null) return null;

		Vec2 point = convertCoordinateSystem(new Vec2(e.after.x, e.after.y));

		Set<FLine> hit = new LinkedHashSet<>();

		Window.Event<Window.MouseState> eMarked = e.copy();
		eMarked.properties.put(interaction, this);

	    List<Mouse.Dragger> draggers = all.stream().filter(f -> f.attributes.get(projectedArea)!=null).filter(f -> f.attributes.get(projectedArea).apply(f).contains(point.x, point.y))
			    .flatMap(f -> f.attributes.getOr(Mouse.onMouseDown, Collections::emptyList).stream())
			    .map(omd -> omd.onMouseDown(eMarked, button)).filter(x -> x != null).collect(Collectors.toList());


		if (draggers.size() > 0) {
			return (event, termination) -> {
				event = event.copy();
				event.properties.put(interaction, this);

				Iterator<Mouse.Dragger> it = draggers.iterator();
				while (it.hasNext()) {
					if (!it.next().update(event, termination)) it.remove();
				}
				return draggers.size() > 0;
			};
		}

		return null;
	}

	Set<FLine> previousIntersection = new LinkedHashSet<>();

	@Override
	public Mouse.Dragger onMouseMove(Window.Event<Window.MouseState> e) {
		if (all == null) return null;

		Vec2 point = convertCoordinateSystem(new Vec2(e.after.x, e.after.y));

		Set<FLine> hit = new LinkedHashSet<FLine>();

		Window.Event<Window.MouseState> eMarked = e.copy();
		eMarked.properties.put(interaction, this);

		Set<FLine> intersects = all.stream().filter(f -> f.attributes.has(projectedArea))
			    .filter(f -> f.attributes.get(projectedArea).apply(f).contains(point.x, point.y)).collect(Collectors.toSet());

		Set<FLine> enter = new LinkedHashSet<FLine>(intersects);
		enter.removeAll(previousIntersection);

		Set<FLine> exit = new LinkedHashSet<FLine>(previousIntersection);
		exit.removeAll(intersects);

		previousIntersection = intersects;

		List<Mouse.Dragger> draggers = intersects.stream()
			    .flatMap(f -> f.attributes.getOr(Mouse.onMouseMove, Collections::emptyList).stream()).map(omd -> omd.onMouseMove(eMarked))
			    .filter(x -> x != null).collect(Collectors.toList());

		draggers.addAll(enter.stream().flatMap(f -> f.attributes.getOr(Mouse.onMouseEnter, Collections::emptyList).stream())
			    .map(omd -> omd.onMouseEnter(eMarked)).filter(x -> x != null).collect(Collectors.toList()));

		exit.stream().flatMap(f -> f.attributes.getOr(Mouse.onMouseExit, Collections::emptyList).stream())
			    .forEach(omd -> omd.onMouseExit(eMarked));


		if (draggers.size() > 0) {
			return (event, termination) -> {
				event = event.copy();
				event.properties.put(interaction, this);

				Iterator<Mouse.Dragger> it = draggers.iterator();
				while (it.hasNext()) {
					if (!it.next().update(event, termination)) it.remove();
				}
				return draggers.size() > 0;
			};
		}


		return null;
	}

}
