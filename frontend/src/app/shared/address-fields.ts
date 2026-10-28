import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Address } from '../core/models';

export function emptyAddress(): Address {
  return { label: '', street: '', number: '', complement: '', district: '', city: '', state: '', zip: '' };
}

export function addressComplete(a: Address): boolean {
  return !!(a.street && a.number && a.district && a.city && /^[A-Za-z]{2}$/.test(a.state) && /^\d{5}-?\d{3}$/.test(a.zip));
}

/** Campos de endereço reutilizados no checkout, pedido manual e cadastro de clientes. */
@Component({
  selector: 'app-address-fields',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let a = address();
    <div class="grid grid-4" style="margin-top:10px">
      <div class="field"><label [for]="id + 'zip'">CEP *</label><input [id]="id + 'zip'" class="input" inputmode="numeric" maxlength="9" autocomplete="postal-code" [ngModel]="a.zip" (ngModelChange)="set('zip', $event)"></div>
      <div class="field span-2"><label [for]="id + 'st'">Rua *</label><input [id]="id + 'st'" class="input" autocomplete="address-line1" [ngModel]="a.street" (ngModelChange)="set('street', $event)"></div>
      <div class="field"><label [for]="id + 'n'">Número *</label><input [id]="id + 'n'" class="input" [ngModel]="a.number" (ngModelChange)="set('number', $event)"></div>
      <div class="field"><label [for]="id + 'c'">Complemento</label><input [id]="id + 'c'" class="input" autocomplete="address-line2" [ngModel]="a.complement" (ngModelChange)="set('complement', $event)"></div>
      <div class="field"><label [for]="id + 'd'">Bairro *</label><input [id]="id + 'd'" class="input" [ngModel]="a.district" (ngModelChange)="set('district', $event)"></div>
      <div class="field"><label [for]="id + 'ci'">Cidade *</label><input [id]="id + 'ci'" class="input" autocomplete="address-level2" [ngModel]="a.city" (ngModelChange)="set('city', $event)"></div>
      <div class="field"><label [for]="id + 'uf'">UF *</label><input [id]="id + 'uf'" class="input" maxlength="2" autocomplete="address-level1" [ngModel]="a.state" (ngModelChange)="set('state', $event.toUpperCase())"></div>
    </div>`,
})
export class AddressFields {
  private static seq = 0;
  readonly id = 'addr' + ++AddressFields.seq + '-';
  readonly address = model.required<Address>();
  set(field: keyof Address, value: string) { this.address.update(a => ({ ...a, [field]: value })); }
}
