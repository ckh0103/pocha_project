import React from 'react';
import OrderItem from './OrderItem';

const OrderSummary = ({ orderItems, placedOrders, onUpdateQuantity, onRemoveItem, onPlaceOrder, onClearOrder }) => {
 // const totalAmount = orderItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const currentOrderTotal = orderItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const totalPlacedOrdersAmount = placedOrders.reduce((sum, orderBatch) => sum + orderBatch.totalAmount, 0);
  const grandTotal = currentOrderTotal + totalPlacedOrdersAmount;
  
  return (
    <div className="order-summary">
      <h3>현재 주문서</h3>
      <div className="order-list-header">
        <span>메뉴</span>
        <span>수량</span>
        <span>가격</span>
        <span></span> {/* For remove button column */}
      </div>
      <div className="order-list">
        {orderItems.length === 0 ? (
          <p className="empty-order">장바구니가 비었습니다.</p>
        ) : (
          orderItems.map((item) => (
            <OrderItem
              key={item.id}
              item={item}
              onUpdateQuantity={onUpdateQuantity}
              onRemoveItem={onRemoveItem}
            />
          ))
        )}
      </div>
      <div className="order-total">
        <span>현재 주문금액</span>
        <span>{currentOrderTotal.toLocaleString()}원</span>
      </div>
      <button className="order-button" onClick={onPlaceOrder} disabled={orderItems.length === 0}>
        주문하기
      </button>
      <button className="clear-button" onClick={onClearOrder} disabled={orderItems.length === 0}>
        현재 주문 전체 삭제
      </button>

      {/* 총 결제 예정 금액 섹션 추가 */}
      <div className="order-total" style={{ marginTop: '20px', borderTop: '1px solid #444', paddingTop: '10px' }}>
        <span>총 결제 예정 금액</span>
        <span>{grandTotal.toLocaleString()}원</span>
      </div>

    </div>

  );
};

export default OrderSummary;