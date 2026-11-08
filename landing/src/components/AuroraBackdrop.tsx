/**
 * Ambient aurora behind the hero, in pure CSS: three soft ribbons in the upper left and lower left, drifting slowly (transform only,
 * so it costs no layout or paint per frame). It is CSS on purpose: the 3D phone owns the page's single WebGL context, and a second
 * context for the React Bits Aurora shader would compete with it (see 02-REACT-BITS-SOURCE-MATRIX.md).
 */
export default function AuroraBackdrop() {
  return <div className="aurora-bg" aria-hidden="true">
    <span className="aurora-band aurora-band-a" />
    <span className="aurora-band aurora-band-b" />
    <span className="aurora-band aurora-band-c" />
    <span className="aurora-band aurora-band-d" />
  </div>;
}
