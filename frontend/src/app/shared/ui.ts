import {
  ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, effect, inject, input, output, viewChild,
} from '@angular/core';

import { ToastService } from '../core/ui.services';
import { Icon } from './icon';

@Component({
  selector: 'app-modal',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dlg class="modal glass-strong" [class.wide]="wide()" (close)="closed.emit()" (cancel)="onCancel($event)"
            [attr.aria-labelledby]="'mt-' + uid">
      <div class="modal-head">
        <h2 [id]="'mt-' + uid">{{ title() }}</h2>
        <button type="button" class="btn btn-ghost btn-icon btn-sm" (click)="close()" aria-label="Fechar">
          <app-icon name="close" />
        </button>
      </div>
      <div class="modal-body"><ng-content /></div>
      <div class="modal-foot"><ng-content select="[footer]" /></div>
    </dialog>`,
  styles: [`
    .modal { width: min(560px, calc(100vw - 32px)); max-height: calc(100vh - 48px); padding: 0; border-radius: 18px;
      color: var(--text); box-shadow: var(--shadow-lg); overflow: auto; opacity: 0; transform: translateY(8px) scale(.98);
      transition: opacity .18s var(--ease), transform .18s var(--ease), overlay .18s allow-discrete, display .18s allow-discrete; }
    .modal.wide { width: min(880px, calc(100vw - 32px)); }
    .modal[open] { opacity: 1; transform: none; }
    @starting-style { .modal[open] { opacity: 0; transform: translateY(8px) scale(.98); } }
    .modal::backdrop { background: rgba(10, 15, 30, .45); backdrop-filter: blur(3px); }
    .modal-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px 0; }
    .modal-head h2 { margin: 0; font-size: 1.1rem; }
    .modal-body { padding: 14px 20px; }
    .modal-foot { padding: 0 20px 18px; display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
    .modal-foot:empty { display: none; }
  `],
})
export class Modal {
  private static seq = 0;
  readonly uid = ++Modal.seq;
  readonly title = input('');
  readonly open = input(false, { transform: booleanAttribute });
  readonly wide = input(false, { transform: booleanAttribute });

  readonly busy = input(false);
  readonly closed = output<void>();
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');
  private opener: HTMLElement | null = null;

  constructor() {
    effect(() => {
      const d = this.dlg().nativeElement;
      if (this.open() && !d.open) {
        this.opener = document.activeElement as HTMLElement;
        d.showModal();
      } else if (!this.open() && d.open) {
        d.close();
        this.opener?.focus?.();
      }
    });
  }

  close() {
    if (!this.busy()) this.dlg().nativeElement.close();
  }

  onCancel(e: Event) {
    if (this.busy()) e.preventDefault();
  }
}

@Component({
  selector: 'app-empty',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty fade-in">
      <div class="icon-wrap"><app-icon [name]="icon()" [size]="24" /></div>
      <h3>{{ title() }}</h3>
      @if (text()) { <p>{{ text() }}</p> }
      <ng-content />
    </div>`,
})
export class Empty {
  readonly icon = input('box');
  readonly title = input.required<string>();
  readonly text = input('');
}

@Component({
  selector: 'app-pager',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 0) {
      <nav class="pagination" aria-label="Paginação">
        <span>{{ total() }} registro(s) · página {{ page() + 1 }} de {{ pages() || 1 }}</span>
        <div class="row">
          <button class="btn btn-sm" type="button" [disabled]="page() === 0" (click)="change.emit(page() - 1)" aria-label="Página anterior">
            <app-icon name="chevronLeft" /> Anterior
          </button>
          <button class="btn btn-sm" type="button" [disabled]="page() + 1 >= pages()" (click)="change.emit(page() + 1)" aria-label="Próxima página">
            Próxima <app-icon name="chevronRight" />
          </button>
        </div>
      </nav>
    }`,
})
export class Pager {
  readonly page = input(0);
  readonly pages = input(0);
  readonly total = input(0);
  readonly change = output<number>();
}

@Component({
  selector: 'app-table-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-wrap" aria-busy="true" aria-label="Carregando">
      <table class="table"><tbody>
        @for (r of rowsArr(); track $index) {
          <tr>@for (c of colsArr(); track $index) { <td><span class="skeleton line" [style.width.%]="60 + (($index * 13 + r) % 35)"></span></td> }</tr>
        }
      </tbody></table>
    </div>`,
})
export class TableSkeleton {
  readonly rows = input(6);
  readonly cols = input(5);
  rowsArr() { return Array.from({ length: this.rows() }, (_, i) => i); }
  colsArr() { return Array.from({ length: this.cols() }, (_, i) => i); }
}

@Component({
  selector: 'app-toasts',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" role="status" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div [class]="'toast glass-strong ' + t.kind">
          <app-icon [name]="t.kind === 'success' ? 'check' : t.kind === 'error' ? 'alert' : 'info'" />
          <span>{{ t.text }}</span>
          <button type="button" class="btn btn-ghost btn-icon btn-sm" (click)="toast.dismiss(t.id)" aria-label="Fechar notificação">
            <app-icon name="close" [size]="14" />
          </button>
        </div>
      }
    </div>`,
  styles: [`
    .toasts { position: fixed; right: 16px; bottom: 16px; z-index: 1000; display: flex; flex-direction: column; gap: 8px; max-width: min(420px, calc(100vw - 32px)); }
    .toast { display: flex; align-items: center; gap: 10px; padding: 10px 10px 10px 14px; border-radius: 12px; box-shadow: var(--shadow); animation: fadeIn .2s var(--ease); font-size: .92rem; }
    .toast span { flex: 1; }
    .toast.success app-icon { color: var(--success); }
    .toast.error { border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
    .toast.error app-icon { color: var(--danger); }
  `],
})
export class Toasts {
  readonly toast = inject(ToastService);
}
