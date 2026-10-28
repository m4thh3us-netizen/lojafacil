import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, debounce, of, switchMap, tap, timer } from 'rxjs';

import { Page } from '../core/models';
import { errorMessage } from '../core/ui.services';

/**
 * Lista paginada no servidor.
 * - reload(true): usado ao digitar na busca (espera 300 ms sem digitação = debounce).
 * - switchMap: se uma nova busca começa, a anterior é cancelada (evita resultados fora de ordem).
 * - O conteúdo anterior continua na tela durante o carregamento (sem "pulos" de layout).
 * Deve ser criado no contexto de injeção (campo de componente).
 */
export class PagedList<T> {
  readonly data = signal<Page<T> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  page = 0;
  private trigger = new Subject<boolean>();

  constructor(fetch: (page: number) => Observable<Page<T>>) {
    this.trigger.pipe(
      debounce(debounced => (debounced ? timer(300) : of(0))),
      tap(() => { this.loading.set(true); this.error.set(''); }),
      switchMap(() => fetch(this.page).pipe(catchError(e => { this.error.set(errorMessage(e)); return of(null); }))),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(p => {
      if (p) this.data.set(p);
      this.loading.set(false);
    });
  }

  /** Nova busca/filtro: volta para a primeira página. */
  search(debounced = false) {
    this.page = 0;
    this.trigger.next(debounced);
  }

  goTo(page: number) {
    this.page = page;
    this.trigger.next(false);
  }

  refresh() {
    this.trigger.next(false);
  }

  get items(): T[] {
    return this.data()?.content ?? [];
  }

  /** Mostra skeleton só no primeiro carregamento; depois mantém os dados e indica atualização. */
  get firstLoad(): boolean {
    return this.loading() && !this.data();
  }
}
