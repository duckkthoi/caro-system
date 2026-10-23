import type { Mark } from '../api/protocol';

interface Props {
  board: string[];
  lastMove?: [number, number] | null;
  winLine?: [number, number][] | null;
  /** My mark, previewed under the mouse on empty cells. Null when I cannot play. */
  playable?: Mark | null;
  onPlay?: (x: number, y: number) => void;
}

/** A caro board: stones go inside the cells. Highlights the last move and the winning five. */
export function Board({ board, lastMove, winLine, playable = null, onPlay }: Props) {
  const size = board.length;
  const win = new Set((winLine ?? []).map(([x, y]) => `${x},${y}`));
  return (
    <div className="board-wrap">
      <div className="board" style={{ gridTemplateColumns: `repeat(${size}, 1fr)`, gridTemplateRows: `repeat(${size}, 1fr)` }}>
        {board.flatMap((row, y) =>
          [...row].map((cell, x) => {
            const empty = cell === '.';
            const classes = ['cell'];
            if (win.has(`${x},${y}`)) classes.push('win');
            else if (lastMove && lastMove[0] === x && lastMove[1] === y) classes.push('last');
            if (empty && playable) classes.push('playable', `preview-${playable}`);
            return (
              <button
                key={`${x},${y}`}
                type="button"
                className={classes.join(' ')}
                disabled={!empty || !playable}
                onClick={() => onPlay?.(x, y)}
                aria-label={`Ô ${x}, ${y}${empty ? '' : `: ${cell}`}`}
              >
                {!empty && <Stone mark={cell as Mark} />}
              </button>
            );
          }),
        )}
      </div>
    </div>
  );
}

export function Stone({ mark }: { mark: Mark }) {
  return (
    <svg viewBox="0 0 10 10" className={`stone stone-${mark}`} aria-hidden="true">
      {mark === 'X' ? (
        <path d="M2 2 L8 8 M8 2 L2 8" />
      ) : (
        <circle cx="5" cy="5" r="3.2" />
      )}
    </svg>
  );
}
