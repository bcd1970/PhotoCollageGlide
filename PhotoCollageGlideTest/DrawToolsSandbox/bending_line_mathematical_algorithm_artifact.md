# Bending Line – Mathematical Algorithm (Artifact)

This artifact specifies a geometry‑only method for bending a straight segment when the end point is dragged. The bend increases as the end approaches the start, and the curvature distribution is visually proportional along the length.

---

## Definitions
- **Given**: Start point \(\mathbf P_0\in\mathbb R^2\) (fixed), end point \(\mathbf E\in\mathbb R^2\) (draggable).
- **Baseline at creation**: end \(\mathbf E_0\), length \(L_0 = \lVert \mathbf E_0 - \mathbf P_0 \rVert\).
- **Current chord**: \(\mathbf c = \mathbf E - \mathbf P_0\), \(d = \lVert \mathbf c \rVert\).
- **Basis** (choose one):
  - *Original‑basis*: \(\mathbf u = \operatorname{normalize}(\mathbf E_0 - \mathbf P_0)\).
  - *Current‑basis*: \(\mathbf u = \operatorname{normalize}(\mathbf c)\).
  In both cases, \(\mathbf v = R_{90^\circ}\,\mathbf u\) (\(\mathbf v\) is \(\mathbf u\) rotated +90°).
- **Side sign**: \( s = \operatorname{sign}\big((\mathbf E - \mathbf P_0)\cdot\mathbf v\big)\). If zero, reuse last nonzero sign or set \(s=+1\).

---

## Bend strength (increases as \(d\) decreases)
- **Proximity factor**: \( m = \operatorname{clamp}_{[0,1]}\big(1 - d/L_0\big) \).
- **Amplitude (unsigned)**: \( A_\text{raw} = k\,L_0\, m^p \), with tunables \(k\in[0.1,0.4]\), \(p\in[1,2]\).
- **Clamp**: \( A_{\max} = \alpha L_0\) (\(\alpha\in[0.3,0.8]\)); \( A = s\,\operatorname{clamp}_{[\,0,\,A_{\max}\,]}(A_\text{raw})\).

The mapping is scale‑invariant (all lengths scale with \(L_0\)).

---

## Symmetric cubic Bézier construction (proportional bulge)
Represent the bent line as a cubic Bézier \((\mathbf P_0, \mathbf P_1, \mathbf P_2, \mathbf P_3)\) with \(\mathbf P_3 = \mathbf E\) and equal perpendicular offsets of the two control points:

\[
\begin{aligned}
\mathbf P_1 &= \mathbf P_0 + \tfrac{1}{3}\,\mathbf c + A\,\mathbf v,\\[2pt]
\mathbf P_2 &= \mathbf P_0 + \tfrac{2}{3}\,\mathbf c + A\,\mathbf v,\\[2pt]
\mathbf P_3 &= \mathbf E.
\end{aligned}
\]

Curve equation for rendering / sampling:
\[
\mathbf B(t) = (1-t)^3\,\mathbf P_0 + 3(1-t)^2 t\,\mathbf P_1 + 3(1-t)t^2\,\mathbf P_2 + t^3\,\mathbf P_3,\quad t\in[0,1].
\]

**Why this looks proportional:** equal offsets of \(\mathbf P_1\) and \(\mathbf P_2\) create a nearly symmetric curvature profile centered along the chord, avoiding end kinks and concentrating “bulge” in the middle.

---

## Constant‑curvature alternative (circular arc)
If you require uniform curvature everywhere, draw a circular arc through \(\mathbf P_0\) and \(\mathbf E\) with sagitta \(s = A\):

- Midpoint of chord: \(\mathbf M = (\mathbf P_0 + \mathbf E)/2\).
- Unit normal to chord (toward side \(s\)): \(\mathbf n = \operatorname{normalize}(R_{90^\circ}\, \mathbf c)\,\operatorname{sign}(s)\).
- Radius: \( R = \dfrac{s^2 + (d/2)^2}{2\,s} \) (take sign of \(s\) for orientation).
- Center: \( \mathbf C = \mathbf M + \Big(R - \dfrac{d^2}{8s}\Big)\,\mathbf n = \mathbf M + \dfrac{s^2 + (d/2)^2 - d^2/4}{2s}\,\mathbf n = \mathbf M + \dfrac{s}{2}\,\mathbf n. \)
  (equivalently, the center lies \(h = \dfrac{d^2}{8s}\) from \(\mathbf M\) along \(\mathbf n\); use your preferred sagitta–radius relation.)
- Arc angle: \( 2\theta \), where \( \sin\theta = (d/2)/|R| \).

Render the arc from \(\mathbf P_0\) to \(\mathbf E\) around center \(\mathbf C\) with sign determined by \(s\).

---

## Algorithm (Bézier option)
1. **Initialization** (once at tool creation): store \(\mathbf P_0\), \(\mathbf E_0\), \(L_0\). If using original‑basis, precompute \(\mathbf u=\operatorname{normalize}(\mathbf E_0-\mathbf P_0)\) and \(\mathbf v=R_{90^\circ}\,\mathbf u\).
2. **Per frame/drag** for current \(\mathbf E\):
   - Compute \(\mathbf c=\mathbf E-\mathbf P_0\), \(d=\lVert\mathbf c\rVert\).
   - If using current‑basis, set \(\mathbf u=\operatorname{normalize}(\mathbf c)\), \(\mathbf v=R_{90^\circ}\,\mathbf u\).
   - Side \(s=\operatorname{sign}((\mathbf E-\mathbf P_0)\cdot\mathbf v)\); if zero, reuse last.
   - Proximity \(m=\operatorname{clamp}_{[0,1]}(1-d/L_0)\).
   - Amplitude \(A = s\,\operatorname{clamp}_{[0,\alpha L_0]}(k\,L_0\, m^p)\).
   - Control points as above; render cubic Bézier.

---

## Edge cases & stability
- If \(d\approx 0\), set \(A=0\) or cap to a small value to avoid loops; optionally snap \(\mathbf E=\mathbf P_0\).
- To avoid flicker when crossing the axis (\(s\) sign changes), apply a small dead‑band on \((\mathbf E-\mathbf P_0)\cdot\mathbf v\) or keep last \(s\) until \(|\cdot|>\varepsilon\).
- Choose \(\alpha\) to prevent self‑intersections (e.g., \(\alpha\in[0.4,0.6]\)).

---

## Recommended defaults
\(k=0.3,\ p=1.5,\ \alpha=0.6\), original‑basis for a stable “memory” of the initial axis.

