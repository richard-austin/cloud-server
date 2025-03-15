import {AfterViewInit, Component, ElementRef, forwardRef, Input, OnInit, ViewChild, ViewEncapsulation} from '@angular/core';
import {FormControl, NG_VALUE_ACCESSOR} from '@angular/forms';
import {timer} from "rxjs";
import {SharedAngularMaterialModule} from '../../shared/shared-angular-material/shared-angular-material.module';

export const CUSTOM_CONROL_VALUE_ACCESSOR: any = {
  provide: NG_VALUE_ACCESSOR,
  useExisting: forwardRef(() => ProductIdInputComponent),
  multi: true,
};
@Component({
    selector: 'app-product-id-input',
    templateUrl: './product-id-input.component.html',
    styleUrls: ['./product-id-input.component.scss'],
    providers: [CUSTOM_CONROL_VALUE_ACCESSOR],
    encapsulation: ViewEncapsulation.None,
    imports: [SharedAngularMaterialModule]
})
export class ProductIdInputComponent implements OnInit, AfterViewInit {
  @Input() formControl!: FormControl;
  @ViewChild('productIdCtl') productIdCtrl!: ElementRef<HTMLInputElement>;
  readonly indexMax: number = 19;
  cursor: number = 0;
  onChanged!: Function;
  onTouched!: Function;

  constructor() {
  }

  handleKeyDown($event: KeyboardEvent, productIdInput: HTMLInputElement) {
    let cursor: number = productIdInput.selectionStart == null ? 0 : productIdInput.selectionStart;

    let code = $event.code

    switch (code) {
      case "Tab":
        return;   // Don't preventDefault on tab to enable tab field switching
      case "ArrowLeft":
      case "Backspace":
        if (cursor > 0)
          --cursor;
        if(cursor > 0 && cursor < (this.indexMax-4) && (cursor + 1) % 5 == 0)
          --cursor;
        break;
      case "ArrowRight":
        if (cursor < this.indexMax)
          ++cursor;
         break;
      case "Home":
        cursor = 0;
        break;
      case "End":
        cursor = this.lastCharPosition();
        break;
      default:
        if (/^[0-9A-Za-z]$/.test($event.key)) {
          if (cursor < this.indexMax) {
            if(cursor > 0 && (cursor+1) % 5 == 0)
              ++cursor;
            productIdInput.value = [productIdInput.value.slice(0, cursor), $event.key.toUpperCase(), productIdInput.value.slice(cursor + 1)].join('');
            ++cursor;
          }
        }
    }
    $event.preventDefault();
    this.getFormControl().setValue(productIdInput.value = this.putDashes(productIdInput.value));
    productIdInput.selectionStart = productIdInput.selectionEnd = cursor;
  }

  setFocus() {
    let productIDInput: HTMLInputElement = this.productIdCtrl.nativeElement;
    this.getFormControl().setValue(this.putDashes(productIDInput.value));
    this.cursor = productIDInput.selectionStart = productIDInput.selectionEnd = 0;

    // f the mouse positioned the cursor after any entered characters, set it t the last character.
    timer(1).subscribe(() => {
      let firstSpace = this.lastCharPosition();
      // @ts-ignore
      if( productIDInput.selectionStart > firstSpace)
        productIDInput.selectionStart = productIDInput.selectionEnd = firstSpace;
    })
   }

  putDashes(input: string): string {
    input = input.replace(/-/g, "");

    let retVal: string = "";
    for (let index: number = 0; index < this.indexMax; ++index) {
      if (input.length > index)
        retVal += input[index];
      else if (index < (this.indexMax-4) )
        retVal += " ";
      if(index > 0 && index < (this.indexMax-4) && (index+1) % 4 == 0)
        retVal += "-"
    }
    return retVal;
  }

  getFormControl(): FormControl {
    return this.formControl;
  }

  lastCharPosition(): number
  {
    let productIDInput: HTMLInputElement = this.productIdCtrl.nativeElement;
    return productIDInput.value.indexOf(" ");
  }

  registerOnChange(fn: Function) {
    this.onChanged = fn;
  }
  registerOnTouched(fn: Function) {
    this.onTouched = fn;
  }
  writeValue(value: any): void {}

  ngOnInit(): void {
  }

  ngAfterViewInit(): void {
  }
}
