package fieldbox.boxes.plugins;

import field.graphics.Window;
import field.graphics.*;
import field.linalg.Mat4;
import field.linalg.Vec2;
import field.linalg.Vec3;
import field.linalg.Vec4;
import field.utility.*;
import fieldbox.boxes.*;
import fieldbox.io.IO;
import fieldbox.ui.FieldBoxWindow;

import java.awt.*;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static field.graphics.StandardFLineDrawing.*;

/**
 * A box that has a 3d drawing canvas inside it.
 */
public class Viewport extends Box implements IO.Loaded, ProvidesGraphicsContext {

	static public final Dict.Prop<Scene> scene = new Dict.Prop<Scene>("scene").type()
		.toCanon()
		.doc("The Scene that's inside this viewport");

	static public final Dict.Prop<Camera> camera = new Dict.Prop<Camera>("camera").type()
		.toCanon()
		.doc("A Camera object for this viewport");

	static public final Dict.Prop<IdempotencyMap<Supplier<FLine>>> lines3 = new Dict.Prop<>("lines3").type()
		.toCanon()
		.doc("3D Geometry to be drawn along with this box")
		.autoConstructs(() -> new IdempotencyMap<>(Supplier.class));

	static public final Dict.Prop<IdempotencyMap<Supplier<FLine>>> pointSelection = new Dict.Prop<>("pointSelection").type()
		.toCanon()
		.doc("adding lines to this property will additionally make their control points selectable")
		.autoConstructs(() -> new IdempotencyMap<>(Supplier.class));

	static public final Dict.Prop<IdempotencyMap<Supplier<Collection<Supplier<FLine>>>>> bulkLines3 = new Dict.Prop<>("bulkLines3").type()
		.toCanon()
		.doc("3D Geometry to be drawn along with this box")
		.autoConstructs(() -> new IdempotencyMap<>(Supplier.class));

	static public final Dict.Prop<Boolean> clips = new Dict.Prop<>("clips").type()
		.toCanon()
		.doc("set to `true` to have this viewport clip its contents to its frame").set(IO.persistent, true);

	private Drawing drawing;


	/**
	 * shaders for standard drawing of points lines and planes (with 3d camera transform)
	 */
	public class Standard {
		private final Camera camera;

		BaseMesh triangles = BaseMesh.triangleList(0, 0);
		MeshBuilder triangles_builder = new MeshBuilder(triangles);
		BaseMesh lines = BaseMesh.lineList(0, 0);

		MeshBuilder lines_builder = new MeshBuilder(lines);
		BaseMesh points = BaseMesh.pointList(0);
		MeshBuilder points_builder = new MeshBuilder(points);

		Shader trianglesAndLinesShader = new Shader();

		Shader pointShader = new Shader();

		public Standard(Camera camera) {
			this.camera = camera;
			trianglesAndLinesShader.addSource(Shader.Type.vertex, "#version 410\n" +
				"layout(location=0) in vec3 position;\n" +
				"layout(location=1) in vec4 color;\n" +
				"out vec4 vcolor;\n" +
				"uniform mat4 _p;\n" +
				"uniform mat4 _mv;\n" +
				"void main()\n" +
				"{\n" +
				"gl_Position = _p * _mv * vec4(position, 1.0); \n" +
				"vcolor = color;\n" +
				"}");

			trianglesAndLinesShader.addSource(Shader.Type.fragment, "#version 410\n" +
				"layout(location=0) out vec4 _output;\n" +
				"in vec4 vcolor;\n" +
				"uniform float opacity; \n" +
				"uniform float zoffset; \n" +
				"void main()\n" +
				"{\n" +
				"	float f = mod(gl_FragCoord.x-gl_FragCoord.y,20)/20.0;\n" +
				"	f = (sin(f*3.14*2)+1)/2;" +
				"	f = (smoothstep(0.45, 0.55, f)+1)/2;" +
				"	_output  = vec4(abs(vcolor.xyzw));\n" +
				"	if (vcolor.w<0) _output.w *= f;" +
				"	_output.w *= opacity;\n" +
				"	gl_FragDepth = gl_FragCoord.z+zoffset;\n" +
//				    " _output = vec4(1,0,1,1);" +
				"}");

			// camera
			trianglesAndLinesShader.attach(new Uniform<Mat4>("_p", () -> camera.projectionMatrix(drawing==null ? 0 : drawing.displayZ)));
			trianglesAndLinesShader.attach(new Uniform<Mat4>("_mv", () ->  camera.view(drawing==null ? 0 : drawing.displayZ)));
			trianglesAndLinesShader.attach(new Uniform<Float>("opacity", () -> 1f));


			pointShader.addSource(Shader.Type.vertex, "#version 410\n" +
				"layout(location=0) in vec3 position;\n" +
				"layout(location=1) in vec4 color;\n" +
				"layout(location=2) in vec2 pointControl;\n" +
				"out vec4 vcolor_q;\n" +
				"uniform mat4 _p;\n" +
				"uniform mat4 _mv;\n" +
				"out vec2 pc_q;\n" +
				"void main()\n" +
				"{\n" +
				"gl_Position = _p * _mv *  vec4(position, 1.0); \n" +
				"   vcolor_q = color;\n" +
				"   pc_q= pointControl;\n" +
				"}");

			pointShader.addSource(Shader.Type.geometry, "#version 410\n" +
				"layout(points) in;\n" +
				"layout(triangle_strip, max_vertices=4) out;\n" +
				"in vec4[] vcolor_q;\n" +
				"in vec2[] pc_q;\n" +
				"out vec4 vcolor;\n" +
				"out vec2 tc;\n" +
				"out vec2 pc;\n" +
				"uniform vec2 bounds;\n" +
				"void main()\n" +
				"{\n" +
				"float s1 = (pc_q[0].x+2)/bounds.x;\n" +
				"float s2 = s1*bounds.x/bounds.y;\n" +
				"vcolor = vcolor_q[0];\n" +
				"pc = pc_q[0]\n;" +
				"tc = vec2(-1,-1);\n" +
				"gl_Position = gl_in[0].gl_Position+vec4(-s1, -s2, 0, 0)*gl_in[0].gl_Position.w;\n" +
				"EmitVertex();\n" +
				"vcolor = vcolor_q[0];\n" +
				"tc = vec2(1,-1);\n" +
				"gl_Position = gl_in[0].gl_Position+vec4(s1, -s2, 0, 0)*gl_in[0].gl_Position.w;\n" +
				"EmitVertex();\n" +
				"vcolor = vcolor_q[0];\n" +
				"tc = vec2(-1,1);\n" +
				"gl_Position = gl_in[0].gl_Position+vec4(-s1, s2, 0, 0)*gl_in[0].gl_Position.w;\n" +
				"EmitVertex();\n" +
				"vcolor = vcolor_q[0];\n" +
				"tc = vec2(1,1);\n" +
				"gl_Position = gl_in[0].gl_Position+vec4(s1, s2, 0, 0)*gl_in[0].gl_Position.w;\n" +
				"EmitVertex();\n" +
				"EndPrimitive();\n" +
				"}");

			pointShader.addSource(Shader.Type.fragment, "#version 410\n" +
				"layout(location=0) out vec4 _output;\n" +
				"in vec4 vcolor;\n" +
				"in vec2 tc;\n" +
				"uniform float opacity; \n" +
				"void main()\n" +
				"{ \n" +
				"	float f = mod(gl_FragCoord.x-gl_FragCoord.y,20)/20.0;\n" +
				"	f = (sin(f*3.14*2)+1)/2;\n" +
				"	f = (smoothstep(0.45, 0.55, f)+1)/2;\n" +
				"	_output  = vec4(abs(vcolor.xyzw)*smoothstep(0.1, 0.2, (1-(length(tc.xy)))));\n" +
				"	if (vcolor.w<0) _output.w *= f;" +
				"	_output.w *= opacity;\n" +
				"if (_output.w<0.01) discard;\n" +
				"}");

			pointShader.attach(new Uniform<Mat4>("_p", () -> camera.projectionMatrix(drawing==null ? 0 : drawing.displayZ)));
			pointShader.attach(new Uniform<Mat4>("_mv", () -> camera.view(drawing==null ? 0 : drawing.displayZ)));
			pointShader.attach(new Uniform<Float>("opacity", () -> 1f));
			pointShader.attach(new Uniform<Vec2>("bounds", () -> new Vec2(properties.get(Box.frame).w, properties.get(Box.frame).h)));

			Viewport.this.properties.get(scene).attach(trianglesAndLinesShader);
			Viewport.this.properties.get(scene).attach(pointShader);
			pointShader.attach(points);
			trianglesAndLinesShader.attach(triangles);
			trianglesAndLinesShader.attach(lines);


			triangles.asMap_set("zoffset", (Supplier<Float>) () -> 0.000005f);
			lines.asMap_set("zoffset", (Supplier<Float>) () -> -0.000005f);

		}
	}

	Standard standard;
	FLinePointHitTest pointHitTest;

	public Viewport() {
		this.properties.putToList(Drawing.lateDrawers, this::drawNow);
		this.properties.put(scene, new Scene());
		Camera camera = new Camera();
		this.properties.put(Viewport.camera, camera);
		standard = new Standard(camera);
		this.properties.put(clips, true);

		this.properties.putToMap(FLineDrawing.frameDrawing, "__outline__", new Cached<Box, Object, FLine>((box, previously) -> {
			Rect rect = box.properties.get(frame);
			if (rect == null) return null;

			boolean selected = box.properties.isTrue(Mouse.isSelected, false);

			FLine f = new FLine();
			if (selected) rect = rect.inset(-8f);
			else rect = rect.inset(-0.5f);

			f.moveTo(rect.x, rect.y);
			f.lineTo(rect.x + rect.w, rect.y);
			f.lineTo(rect.x + rect.w, rect.y + rect.h);
			f.lineTo(rect.x, rect.y + rect.h);
			f.lineTo(rect.x, rect.y);

			f.attributes.put(strokeColor, selected ? new Vec4(0, 0, 0, -1.0f) : new Vec4(0, 0, 0, 0.5f));
			f.attributes.put(thicken, new BasicStroke(selected ? 16 : 1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			f.attributes.put(stroked, true);

			return f;
		}, (box) -> new Pair(box.properties.get(frame), box.properties.get(Mouse.isSelected))));

		this.properties.putToMap(FLineDrawing.frameDrawing, "__outlineFill__", new Cached<Box, Object, FLine>((box, previously) -> {
			Rect rect = box.properties.get(frame);
			if (rect == null) return null;

			boolean selected = box.properties.isTrue(Mouse.isSelected, false);

			FLine f = new FLine();
			if (selected) rect = rect.inset(-8f);
			else rect = rect.inset(-0.5f);

			f.moveTo(rect.x, rect.y);
			f.lineTo(rect.x + rect.w, rect.y);
			f.lineTo(rect.x + rect.w, rect.y + rect.h);
			f.lineTo(rect.x, rect.y + rect.h);
			f.lineTo(rect.x, rect.y);

			f.attributes.put(stroked, false);
			f.attributes.put(fillColor, new Vec4(0, 0, 0, 0.2f));
			f.attributes.put(filled, true);

			return f;
		}, (box) -> new Pair(box.properties.get(frame), box.properties.get(Mouse.isSelected))));

		pointHitTest = new FLinePointHitTest(new FLinePointHitTest.Transformer() {
			public Rect f;
			public Mat4 t;

			@Override
			public boolean begin(Window.Event<Window.MouseState> provokedBy) {

				Mat4 p = camera.projectionMatrix();
				Mat4 v = camera.view();

				p.transpose();
				v.transpose();

				t = new Mat4(p).mul(v);

				f = properties.get(Box.frame);

				return true;
			}

			@Override
			public void result(List<FLinePointHitTest.Hit> hit) {

			}

			Vec3 tmp = new Vec3();

			@Override
			public Vec2 apply(Vec3 x) {
				x.mulProject(t, tmp);

				// this gets us ndc?
				double xx = (tmp.x + 1) / 2 * f.w + f.x;
				double yy = (-tmp.y + 1) / 2 * f.h + f.y;

				return new Vec2(xx, yy);
			}
		});


		properties.putToMap(Mouse.onMouseDown, "__pointHit__", (e, b) -> {

			// TODO: recur
			IdempotencyMap<Supplier<FLine>> m = properties.get(pointSelection);
			if (m == null) return null;

			List<FLine> allPointLines = m.values().stream().filter(x -> x != null).map(x -> x.get()).filter(x -> x != null).collect(Collectors.toList());

			List<FLinePointHitTest.Hit> hit = pointHitTest.hit(e, allPointLines, 10);

			if (hit.size() > 0) {
				if (previous == null || !previous.equals(hit)) {
					selectPoint(allPointLines, hit.get(0));
				} else {
					selectPoint(allPointLines, hit.get(hitIndex++ % hit.size()));
				}
			} else {
				deselectAllPoints(allPointLines);
			}


			return null;
		});
	}

	private void deselectAllPoints(List<FLine> all) {

		//TODO:

	}

	private void selectPoint(List<FLine> all, FLinePointHitTest.Hit hit) {
		deselectAllPoints(all);

		//TOOD?

	}


	int hitIndex = 0;
	List<FLinePointHitTest.Hit> previous = null;


	@Override
	public void loaded() {
		drawing = this.first(Drawing.drawing)
			.get();

	}

	protected void drawNow(DrawingInterface context) {

		Camera c = this.properties.get(Viewport.camera);
		if (c != null) {
			c.advanceState(x -> {
				Rect f = this.properties.get(Box.frame);
				x.aspect = f.w / f.h;
				return x;
			});
		}

		try (Util.ExceptionlessAutoClosable s = GraphicsContext.getContext().stateTracker.save()) {

			Rect f = this.properties.get(Box.frame);

			boolean clips = this.properties.isTrue(Viewport.clips, true);
			Optional<Drawing> od = this.first(Drawing.drawing);

			// TODO, blast radius of change to DrawingInterface? Viewports cannot clip properly on non-window renderers
			Vec2 tl = od.get().drawingSystemToWindowSystem(new Vec2(f.x, f.y));
			Vec2 bl = od.get().drawingSystemToWindowSystem(new Vec2(f.x + f.w, f.y + f.h));

			FieldBoxWindow window = this.first(Boxes.window).get();
			int h = window.getHeight();
			int w = window.getWidth();
			float rs = window.getRetinaScaleFactor();
			int[] v = new int[]{(int) ((int) tl.x * rs) + 5 + 10, (int) ((int) (h - bl.y) * rs) + 3 + 13, (int) ((int) (bl.x - tl.x + 2) * rs) - 9 - 20, (int) ((int) (bl.y - tl.y + 2) * rs - 12 - 18)};

			if (clips) {
				GraphicsContext.getContext().stateTracker.scissor.set(v);
				GraphicsContext.getContext().stateTracker.viewport.set(v);
			}

			Map<String, Supplier<FLine>> q = breadthFirst(downwards()).filter(x -> x.properties.has(lines3))
				.flatMap(x -> x.properties.get(lines3)
					.entrySet()
					.stream())
				.collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue(), (old, n) -> old));
			Map<String, Supplier<Collection<Supplier<FLine>>>> q2 = breadthFirst(downwards()).filter(x -> x.properties.has(bulkLines3))
				.flatMap(x -> x.properties.get(bulkLines3)
					.entrySet()
					.stream())
				.collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue(), (old, n) -> old));

			standard.lines_builder.open();
			standard.triangles_builder.open();
			standard.points_builder.open();

			try {
				q.values()
					.stream()
					.map(x -> x.get())
					.filter(x -> x != null)
					.forEach(x -> StandardFLineDrawing.dispatchLine(x, standard.triangles_builder, standard.lines_builder, standard.points_builder, Optional.empty(), ""));
				q2.values()
					.stream()
					.map(x -> x.get())
					.filter(x -> x != null)
					.flatMap(x -> x.stream())
					.filter(x -> x != null)
					.map(x -> x.get())
					.filter(x -> x != null)
					.forEach(x -> StandardFLineDrawing.dispatchLine(x, standard.triangles_builder, standard.lines_builder, standard.points_builder, Optional.empty(), ""));
			} finally {
				standard.points_builder.close();
				standard.triangles_builder.close();
				standard.lines_builder.close();
			}
			Scene scene = this.properties.get(Viewport.scene);

			scene.updateAll();
		}

	}


	@Override
	public String toString() {
		return super.toString() + "/viewport";
	}

	@Override
	public GraphicsContext getGraphicsContext() {
		return this.find(Boxes.window, both())
			.findFirst()
			.get()
			.getGraphicsContext();
	}

//	@Override
	public Vec3 drawingSpaceToViewport(Vec2 drawingSpace, float z) {
		Camera c = properties.get(Viewport.camera);

		Rect r = properties.get(Box.frame);


		Vec4 oo= new Vec4();
		Mat4.unproject((drawingSpace.x-r.x)/r.w,(r.y+r.h-drawingSpace.y)/r.h, z, c.projectionMatrix(drawing.displayZ).transpose(), c.view(drawing.displayZ).transpose(), aa, new Mat4(), oo);

		System.out.println(" OUT :"+oo);

		return new Vec3(oo.x, oo.y, oo.z);
	}

	IntBuffer aa = IntBuffer.allocate(4);
	{
		aa.put(new int[]{0, 0, 1, 1});
		aa.rewind();
	}

	//	@Override
	public Vec2 viewportToDrawingSpace(Vec3 space) {

		Camera c = properties.get(Viewport.camera);

		Rect r = properties.get(Box.frame);

		Vec4 out = new Vec4();

		Mat4.project(space, c.projectionMatrix(drawing.displayZ).transpose(), c.view(drawing.displayZ).transpose(), aa, out);

		return new Vec2(out.x*r.w+r.x, r.y+r.h-out.y*r.h);
	}
}
