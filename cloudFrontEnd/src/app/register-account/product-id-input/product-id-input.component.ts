import {AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild, ViewEncapsulation} from '@angular/core';
import {FormControl} from '@angular/forms';

@Component({
  selector: 'app-product-id-input',
  templateUrl: './product-id-input.component.html',
  styleUrls: ['./product-id-input.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ProductIdInputComponent implements OnInit, AfterViewInit {
  @Input() formControl!: FormControl;
  @ViewChild('productIdCtl') productIdCtl!: ElementRef<HTMLInputElement>;
  readonly indexMax: number = 19;
  readonly productId: string[];
  cursor: number = 0;

  constructor() {
    this.productId = new Array<string>(this.indexMax);
    this.productId[0] = '\xa0\xa0\xa0\xa0-\xa0\xa0\xa0\xa0-\xa0\xa0\xa0\xa0-\xa0\xa0\xa0\xa0';
  }


  setValue() {
    //this.formControl.setValue('AV6Y-AS');
  }

  getFormControl(): FormControl {
    return this.formControl;
  }

  ngOnInit(): void {
    this.setValue();
  }

  ngAfterViewInit(): void {
    // this.productIdCtl.nativeElement.onkeypress= (ev:KeyboardEvent) => {
    //   let valid: RegExp = new RegExp("[A-Za-z0-9]")
    //   this.productIdCtl.nativeElement.value = this.split_product_key(this.productIdCtl.nativeElement.value);
    //   //this.productId.nativeElement.setSelectionRange(2,2);
    //   this.productIdCtl.nativeElement.value = this.productIdCtl.nativeElement.value.toUpperCase();
    //   if(!valid.test(ev.key))
    //       ev.preventDefault();
    // };
  }

  handleKeyPress($event: KeyboardEvent, productIdInput: HTMLInputElement) {
    let y = $event;
    let curs = $event.target;
    let x = productIdInput.selectionStart;

    // if(this.cursor < this.indexMax)
    //   this.productId[this.cursor] = $event.key;
    // if (this.cursor < this.indexMax)
    //   ++this.cursor;
  }

  handleKeyDown($event: KeyboardEvent, productIdInput: HTMLInputElement) {
    let cursor: number = productIdInput.selectionStart == null ? 0 : productIdInput.selectionStart;
    $event.preventDefault();

    let code = $event.code
    switch (code) {
      case "ArrowLeft":
      case "Backspace":
        if (cursor > 0)
          --cursor;
        if(cursor > 0 && cursor < (this.indexMax-4) && (cursor + 1) % 5 == 0)
          --cursor;
        break;
      // case "ArrowLeft"
      //   if (cursor > 0)
      //     --cursor;
      //   break;
      case "ArrowRight":
        if (cursor < this.indexMax)
          ++cursor;
        // if(cursor > 0 && cursor < (this.indexMax-4) && (cursor+1) % 5 == 0)
        //   ++cursor;
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
    productIdInput.value = this.putDashes(productIdInput.value);
    productIdInput.selectionStart = productIdInput.selectionEnd = cursor;
  }

  putDashes(input: any): string {
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

  // select($event: MouseEvent, index: number) {
  //   this.cursor = index;
  // }

}
